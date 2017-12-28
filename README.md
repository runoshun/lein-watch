# lein-watch

A Leiningen plugin to watch directories and run tasks automatically.

## Usage

Put [![Clojars Project](https://img.shields.io/clojars/v/yogthos/lein-watch.svg)](https://clojars.org/yogthos/lein-watch)
into the `:plugins` vector of your `project.clj` and add :watch configuration to your project.clj.

Example configuration (run compile task when .clj file is changed):

    (defproject sample-project
      ...
      :watch {
        :rate 500
        :color :red
        :watchers {
          :compile {
            :watch-dirs ["src"]
            :file-patterns [#"\.clj"]
            :tasks ["compile"]}}}
      ...)

and just run watch task

    $ lein watch

See [sample-project](https://github.com/yogthos/lein-watch/tree/master/sample-project) for more complex usage.

## License

Distributed under the MIT License.

Copyright © 2014 runoshun
