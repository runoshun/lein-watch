(defproject watch-sample "0.1.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ;; use 'lein-watch' plugin
  :plugins [[lein-watch "0.0.3"]
            [lein-garden "0.1.8"]]
  :dependencies [[org.clojure/clojure "1.5.1"]]

  ;; profiles used in watch tasks
  :profiles {:garden {:source-paths ["src-garden"]
                      :dependencies [[garden "1.1.5"]]}
             :hiccup {:source-paths ["src-hiccup"]
                      :dependencies [[hiccup "1.0.5"]]}}

  :garden {
    :builds [{:id "screen"
              :stylesheet test-watch.css/screen
              :compiler {:output-to "resources/public/screen.css"
                         :pretty-print true}}]}

  ;; configuration for 'lein-watch'
  :watch {
    ;; polling rate in 'ms' (it's directory passed to 'watchtower')
    :rate 300

    ;; watcher definition
    :watchers {
      ;; run 'lein garden once' when *.clj file under the 'src-garden' is changed.
      :garden {;; :watch-dirs (required) : vector of string
               ;;   Put directories that you want watch.
               :watch-dirs ["src-garden"]

               ;; :file-patterns (optional) : vector of java.util.regex.Pattern
               ;;   If file name that changed is matched this patterns, tasks are executed.
               ;;   otherwise not executed.
               ;;   default : [#".*"].
               :file-patterns [#"\.clj"]

               ;; :tasks (required) : vector of (string|symbol|list)
               ;;   Put tasks that you want executed when file is changed.
               ;;   If a value is string, it is evaluated as a leiningen task.
               ;;   If a value is symbol, it is called as a function in project context and
               ;;   is passed changed file as argument.
               ;;   If a value is list, it is evaluated in project context and changed file is
               ;;    appended last of argument.
               :tasks ["garden once"]

               ;; profiles (optional) : vector of keyword
               ;;   profile names used when executing tasks.
               ;;   default : []
               :profiles [:garden]}

      ;; call 'test-watch.hiccup/generate' (defined in 'src-hiccup/test_watch/hiccup.clj')
      ;; when *.clj file under the 'src-hiccup' is changed.
      :hiccup {:watch-dirs ["src-hiccup"]
               :profiles [:hiccup]
               :file-patterns [#"\.clj"]
               :tasks [(test-watch.hiccup/generate "resources/public/index.html")]}}})

