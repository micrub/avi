(ns avi.t-nfa
  (:require [avi.nfa :refer :all]
            [midje.sweet :refer :all]))

(tabular
  (facts "about NFAs"
    (let [nfa ?nfa
          final-state (reduce
                        (fn [s input]
                          (if (= :reject s)
                            :reject
                            (advance nfa s input :reject)))
                        (start nfa)
                        ?inputs)
          result (cond
                   (= :reject final-state)
                   :reject

                   (accept? nfa final-state)
                   :accept

                   :else
                   nil)]
      result => ?result))

  ?nfa        ?inputs   ?result
  (match "1") []        nil   
  (match "1") ["1"]     :accept
  (match "1") ["2"]     :reject
  (match "1") ["1" "2"] :reject)
