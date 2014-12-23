(ns leiningen.watch
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [watchtower.core :as wt]
            [leiningen.core.eval :as lein-eval]
            [leiningen.core.main :as lein-main]
            [leiningen.core.project :as lein-project]))

(def ^:private default-watcher-options
  {:file-patterns [#".*"]
   :profiles []})

(def ^:private default-global-settings
  {:rate 300})

(defn- non-nils [coll] (remove nil? coll))

(defn- ensure-regex [pat]
  (if (instance? java.util.regex.Pattern pat)
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
                           (let [options (group watchers)
                                 patterns (-> options :file-patterns)
                                 dirs (-> options :watch-dirs)]
                             (map (fn [dir] (make-route dir patterns group)) dirs)))
                         (keys watchers))]
      (first (non-nils (map #(% file) routes))))))

(defn- separate-ns [sym]
  (let [syms (string/split (str sym) #"/")]
    (if (= 2 (count syms))
      (symbol (first syms))
      nil)))

(defn- run-tasks [project watcher]
  (let [project (lein-project/merge-profiles project (:profiles watcher))
        tasks (:tasks watcher)]
    (println (str "[lein-watch] run-tasks : " tasks))
    (doseq [task tasks]
      (cond
        (string? task) (lein-main/resolve-and-apply
                         project
                         (string/split task #"\s+"))
        (symbol? task) (let [ns (separate-ns task)
                             form (if ns
                                    `(do (require '~ns) (~task))
                                    `(~task))]
                         (lein-eval/eval-in-project project form))
        (list? task)   (let [ns (separate-ns (first task))
                             form (if ns
                                    `(do (require '~ns) (~@task))
                                    `(~@task))]
                         (lein-eval/eval-in-project project form))))))


(defn- ensure-slash [dir]
  (if-not (.endsWith dir "/")
    (str dir "/")
    dir))

(defn watch
  "Watch directories and run tasks when file changed."
  [project & args]
  (let [settings (:watch project)
        settings (merge default-global-settings settings)
        watchers (:watchers settings)
        watchers (reduce (fn [m [k v]] (assoc m k (merge default-watcher-options v))) {} watchers)
        router (make-router watchers)
        dirs (map ensure-slash (mapcat :watch-dirs (vals watchers)))
        process-event (fn [files]
                        (try
                          (let [files-with-routes (map (fn [file] [(router file) file]) files)
                                route-map (reduce #(update-in %1 [(first %2)] conj (second %2)) {} files-with-routes)]
                            (doseq [route (keys route-map)]
                              (when route
                                (let [files (get route-map route)]
                                  (println (str "[lein-watch] file(s) changed : " (mapv str files)))
                                  (run-tasks project (watchers route))))))
                          (catch Throwable e
                            (println (.getMessage e))
                            (.printStackTrace e))))]
    (if watchers
      (deref
        (wt/watcher dirs
                    (wt/rate (:rate settings))
                    (wt/on-change process-event)))
      (println "no watcher found."))))

(comment
  (do
    (def project
      {:watch
       {:watchers
        {:garden {:watch-dirs ["src-garden"]
                  :file-patterns [#".*"]
                  :tasks ["garden once"]}
         :hiccup {:watch-dirs ["src-hiccup"]
                  :file-patterns [#"\.txt"]
                  :tasks ["hiccpu once"]}}}})

    (def router (make-router (-> project :watch :watchers)))
    (assert (= (router (io/file "src-garden/foo")) :garden))
    (assert (= (router "src-hiccup/bar") nil))
    (assert (= (router "src-hiccup/bar.txt") :hiccup))

    ))
