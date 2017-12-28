(ns leiningen.watch
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [leiningen.core.eval :as lein-eval]
            [leiningen.core.main :as lein-main]
            [leiningen.core.project :as lein-project])
  (:import java.io.File
           java.util.regex.Pattern))

(def ^:private default-watcher-options
  {:file-patterns [#".*"]
   :profiles      []})

(def ^:private default-global-settings
  {:rate 300})

(def ^:private ansi-codes
  {:reset   "\u001b[0m"
   :black   "\u001b[30m" :gray "\u001b[1m\u001b[30m"
   :red     "\u001b[31m" :bright-red "\u001b[1m\u001b[31m"
   :green   "\u001b[32m" :bright-green "\u001b[1m\u001b[32m"
   :yellow  "\u001b[33m" :bright-yellow "\u001b[1m\u001b[33m"
   :blue    "\u001b[34m" :bright-blue "\u001b[1m\u001b[34m"
   :magenta "\u001b[35m" :bright-magenta "\u001b[1m\u001b[35m"
   :cyan    "\u001b[36m" :bright-cyan "\u001b[1m\u001b[36m"
   :white   "\u001b[37m" :bright-white "\u001b[1m\u001b[37m"
   :default "\u001b[39m"})


(defn log [{:keys [color]} & strs]
  (let [text (string/join " " strs)]
    (if color
      (println (str (ansi-codes color) text (ansi-codes :reset)))
      (println text))))

(defn- non-nils [coll] (remove nil? coll))

(defn- ensure-regex [pat]
  (if (instance? Pattern pat)
    pat
    (re-pattern pat)))

(defn- match? [patterns file]
  (boolean
    (some identity (map #(re-find % (str file))
                        (map ensure-regex patterns)))))

(defn- child? [parent child]
  (not (.isAbsolute (.relativize (.toURI (io/file parent))
                                 (.toURI (io/file child))))))

(defn- make-route [dir patterns group]
  (fn [file]
    (if (and (child? dir file) (match? patterns file))
      group
      nil)))

(defn- make-router [watchers]
  (fn [file]
    (let [routes (mapcat (fn [group]
                           (let [options  (group watchers)
                                 patterns (-> options :file-patterns)
                                 dirs     (-> options :watch-dirs)]
                             (map (fn [dir] (make-route dir patterns group)) dirs)))
                         (keys watchers))]
      (first (non-nils (map #(% file) routes))))))

(defn- separate-ns [sym]
  (let [syms (string/split (str sym) #"/")]
    (if (= 2 (count syms))
      (symbol (first syms))
      nil)))

(defn- run-tasks [project watcher]
  (let [project  (lein-project/merge-profiles project (:profiles watcher))
        tasks    (:tasks watcher)
        settings (:watch project)]
    (log (when (:color settings) {:color :white}) "run-tasks:")
    (log settings (str " " (string/join "\n " tasks)))
    (doseq [task tasks]
      (cond
        (string? task) (lein-main/resolve-and-apply
                         project
                         (string/split task #"\s+"))
        (symbol? task) (let [ns   (separate-ns task)
                             form (if ns
                                    `(do (require '~ns) (~task))
                                    `(~task))]
                         (lein-eval/eval-in-project project form))
        (list? task) (let [ns   (separate-ns (first task))
                           form (if ns
                                  `(do (require '~ns) (~@task))
                                  `(~@task))]
                       (lein-eval/eval-in-project project form))))))


(defn- ensure-slash [dir]
  (if-not (.endsWith dir "/")
    (str dir "/")
    dir))

(defn directory-files [dir]
  (->> (io/file dir) (file-seq) (remove (memfn isDirectory))))

(defn modified-since [^File file timestamp]
  (> (.lastModified file) timestamp))

(defn find-files [patterns all-files]
  (reduce
    (fn [selected-files pattern]
      (into selected-files (filter #(re-find pattern (str %)) all-files)))
    #{} patterns))

(defn watcher [{:keys [dirs file-patterns rate process-event]}]
  (loop [time 0]
    (Thread/sleep rate)
    (if-let [files (->> (mapcat directory-files dirs)
                        (find-files file-patterns)
                        (filter #(modified-since % time))
                        (not-empty))]
      (let [time (System/currentTimeMillis)]
        (process-event files)
        (recur time))
      (recur time))))

(defn watch
  "Watch directories and run tasks when file changed."
  [project & args]
  (let [settings      (:watch project)
        color?        (boolean (:color settings))
        settings      (merge default-global-settings settings)
        watchers      (:watchers settings)
        watchers      (reduce (fn [m [k v]] (assoc m k (merge default-watcher-options v))) {} watchers)
        router        (make-router watchers)
        dirs          (map ensure-slash (mapcat :watch-dirs (vals watchers)))
        process-event (fn [files]
                        (try
                          (let [files-with-routes (map (fn [file] [(router file) file]) files)
                                route-map         (reduce #(update-in %1 [(first %2)] conj (second %2)) {} files-with-routes)]
                            (doseq [route (keys route-map)]
                              (when route
                                (let [files (get route-map route)]
                                  (log (when color? {:color :white}) "file(s) changed:")
                                  (log settings (str " " (string/join "\n " (mapv str files))))
                                  (run-tasks project (watchers route))))))
                          (catch Throwable e
                            (log (when color? {:color :red}) (.getMessage e))
                            (.printStackTrace e))))]
    (if watchers
      (deref
        (watcher
          {:dirs          dirs
           :rate          (:rate settings)
           :file-patterns (->> watchers vals (mapcat :file-patterns))
           :process-event process-event}))
      (log (when color? {:color :yellow}) "no watcher found."))))

(comment
  (do
    (def project
      {:watch
       {:watchers
        {:garden {:watch-dirs    ["src-garden"]
                  :file-patterns [#".*"]
                  :tasks         ["garden once"]}
         :hiccup {:watch-dirs    ["src-hiccup"]
                  :file-patterns [#"\.txt"]
                  :tasks         ["hiccpu once"]}}}})

    (def router (make-router (-> project :watch :watchers)))
    (assert (= (router (io/file "src-garden/foo")) :garden))
    (assert (= (router "src-hiccup/bar") nil))
    (assert (= (router "src-hiccup/bar.txt") :hiccup))

    ))
