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
  (boolean (first (non-nils (map #(re-find % (str file))
                                (map ensure-regex  patterns))))))

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

(defn- run-tasks [project watcher file]
  (let [project (lein-project/merge-profiles project (:profiles watcher))
        tasks (:tasks watcher)]
    (println (str "[lein-watch] file changed : " file))
    (println (str "[lein-watch] run-tasks : " tasks))
    (doseq [task tasks]
      (cond
        (string? task) (lein-main/resolve-and-apply
                         project
                         (string/split (string/replace-first task #"%f" (str file)) #"\s+"))
        (symbol task) (let [ns (separate-ns task)
                            file-str (.getAbsolutePath file)
                            form (if ns
                                   `(do (require '~ns) (~task ~file-str))
                                   `(~task ~file-str))]
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
                        (doseq [file files]
                          (run-tasks project (watchers (router file)) file)))]
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
