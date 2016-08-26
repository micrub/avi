(ns avi.layout
  (:require [clojure.spec :as s]))

(s/def ::nat (s/and int? (complement neg?)))
(s/def ::shape (s/tuple (s/tuple ::nat ::nat)
                        (s/tuple ::nat ::nat)))