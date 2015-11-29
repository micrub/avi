(ns avi.t-nfa
  (:require [avi.nfa :refer :all]
            [midje.sweet :refer :all]))

(defn- state-after-inputs
  [nfa inputs]
  (reduce
    (fn [s input]
      (if (= :reject s)
        :reject
        (advance nfa s input :reject)))
    (start nfa)
    inputs))

(defn- characterize-state
  [nfa state]
  (cond
    (= :reject state)   :reject
    (accept? nfa state) :accept
    :else               :pending))

(tabular
  (facts "about NFAs accepting inputs"
    (let [nfa ?nfa
          state (state-after-inputs nfa ?inputs)
          result (characterize-state nfa state)]
      result => ?result))

  ?nfa                         ?inputs  ?result
  (match 1)                    []       :pending
  (match 1)                    [1]      :accept
  (match 1)                    [2]      :reject
  (match 1)                    [1 2]    :reject

  any                          []       :pending
  any                          [1]      :accept
  any                          [2]      :accept
  any                          [1 2]    :reject

  (maybe (match 1))            []       :accept
  (maybe (match 1))            [1]      :accept
  (maybe (match 1))            [2]      :reject
  (maybe (match 1))            [1 1]    :reject
  (maybe (match 1))            [1 2]    :reject

  (choice (match 1))           []       :pending
  (choice (match 1))           [1]      :accept
  (choice (match 1))           [2]      :reject
  (choice (match 1))           [1 1]    :reject
  (choice (match 1))           [1 2]    :reject
  (choice (match 1) (match 2)) []       :pending
  (choice (match 1) (match 2)) [1]      :accept
  (choice (match 1) (match 2)) [2]      :accept
  (choice (match 1) (match 2)) [3]      :reject
  (choice (match 1) (match 2)) [1 1]    :reject
  (choice (match 1) (match 2)) [1 3]    :reject
  (choice (match 1) (match 2)) [3 1]    :reject
  (choice (match 1) (match 2)) [3 3]    :reject

  (kleene (match 1))           []       :accept
  (kleene (match 1))           [1]      :accept
  (kleene (match 1))           [1 1]    :accept
  (kleene (match 1))           [1 1 1]  :accept
  (kleene (match 1))           [2]      :reject
  (kleene (match 1))           [1 2]    :reject
  (kleene (match 1))           [1 1 2]  :reject

  (chain (match 1) (match 2))  []       :pending
  (chain (match 1) (match 2))  [1]      :pending
  (chain (match 1) (match 2))  [1 2]    :accept
  (chain (match 1) (match 2))  [1 2 3]  :reject
  (chain (match 1) (match 2))  [3]      :reject
  (chain (match 1) (match 2))  [1 3]    :reject)

(defn f
  [v d]
  (+ (* 10 (or v 0)) d))

(tabular
  (facts "about NFAs reducing values"
    (let [nfa ?nfa
          state (state-after-inputs nfa ?inputs)
          result (characterize-state nfa state)
          value (accept-value nfa state)]
      result => :accept
      value => ?value))

  ?nfa                                       ?inputs  ?value
  (on (match 1) f)                           [1]      1
  (on (match 2) f)                           [2]      2

  (on any f)                                 [1]      1
  (on any f)                                 [7]      7

  (maybe (on (match 1) f))                   []       nil
  (maybe (on (match 1) f))                   [1]      1
  (on (maybe (match 1)) f)                   []       nil
  (on (maybe (match 1)) f)                   [1]      1

  (choice (on (match 1) f) (on (match 2) f)) [1]      1
  (choice (on (match 1) f) (on (match 2) f)) [2]      2
  (on (choice (match 1) (match 2)) f)        [1]      1
  (on (choice (match 1) (match 2)) f)        [2]      2

  (chain (on any f) any)                     [7 9]    7

  (kleene (on (match 7) f))                  [7]      7
  (kleene (on (match 7) f))                  [7 7]    77
  (kleene (on (match 7) f))                  [7 7 7]  777
  (kleene (on any f))                        [8 6 7]  867

  (chain (on (match 1) f) (on (match 2) f))  [1 2]    12)