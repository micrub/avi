(ns avi.nfa
  (:require [clojure.set :as set]))

(defn- null-reducer
  [accumulator _]
  accumulator)

(defn- merge-transitions
  [& xs]
  (apply merge-with (partial merge-with into) xs))

(defn- mapcat-transitions
  [f xs]
  (->>
    (for [[value froms] xs
          [from tos] froms
          [to reducers] tos]
      (f value from to reducers))
    (reduce concat)
    (reduce
      (fn [xs [value from to reducers]]
        (assoc-in xs [value from to] reducers))
      {})))

(defn- states
  [nfa]
  (->> (for [[_ froms] (:transitions nfa)
             [from tos] froms
             [to _] tos]
         [from to])
    (apply concat)
    (into #{})))

(defn- renumber
  "Renumber the states in each NFA so that no two NFAs share a state number."
  [nfas]
  (second
    (reduce
      (fn [[n done] nfa]
        (let [original (states nfa)
              mapping (zipmap original (map (partial + n) (range)))
              nfa' {:start (->> (:start nfa) (map mapping) (into #{}))
                    :accept (->> (:accept nfa) (map mapping) (into #{}))
                    :transitions (mapcat-transitions
                                   (fn [value from to reducers]
                                     [[value (mapping from) (mapping to) reducers]])
                                   (:transitions nfa))}]
          [(+ n (count mapping)) (conj done nfa')]))
      [0 []]
      nfas)))

(defn match
  [value]
  {:start #{0}
   :accept #{1}
   :transitions {value {0 {1 []}}}})

(def any
  (match ::any))

(defn maybe
  [nfa]
  {:start (:start nfa)
   :accept (set/union (:start nfa) (:accept nfa))
   :transitions (:transitions nfa)})

(defn choice
  ([a]
   a)
  ([a b]
   (let [[a b] (renumber [a b])]
     {:start (set/union (:start a) (:start b))
      :accept (set/union (:accept a) (:accept b))
      :transitions (merge-transitions
                     (:transitions a)
                     (:transitions b))}))
  ([a b & cs]
   (reduce choice (concat [a b] cs))))

(defn kleene
  ([nfa]
   {:start (:start nfa)
    :accept (:start nfa)

    ;; any transition which is x -> a, a ∈ accept, is replace with all
    ;; x -> s ∀ s ∈ start
    :transitions (mapcat-transitions
                   (fn [value from to reducers]
                     (if ((:accept nfa) to)
                       (for [s (:start nfa)]
                         [value from s reducers])
                       [[value from to reducers]]))
                   (:transitions nfa))}))

(defn chain
  ([a]
   a)
  ([a b]
   (let [[a b] (renumber [a b])]
     {:start (:start a)
      :accept (:accept b)
      :transitions (mapcat-transitions
                     (fn [value from to reducers]
                       (if ((:accept a) to)
                         (for [s (:start b)]
                           [value from s reducers])
                         [[value from to reducers]]))
                     (merge-transitions
                       (:transitions a)
                       (:transitions b)))}))
  ([a b & cs]
   (reduce chain (concat [a b] cs))))

(defn on
  [nfa f]
  (update-in nfa [:transitions] (partial
                                  mapcat-transitions
                                  (fn [value from to reducers]
                                    (if ((:accept nfa) to)
                                      [[value from to (conj reducers f)]]
                                      [[value from to reducers]])))))

(defn start
  [nfa]
  (->> (:start nfa)
    (map #(vector % nil))
    (into {})))

(defn accept?
  [nfa state]
  (not (empty? (set/intersection
                 (:accept nfa)
                 (into #{} (keys state))))))

(defn accept-value
  [nfa state]
  (->> state
    (filter (comp (:accept nfa) first))
    (map second)
    first))

(defn advance
  [nfa state input reject-value]
  (let [state' (->> (for [[s targets] (concat
                                        (get-in nfa [:transitions ::any])
                                        (get-in nfa [:transitions input]))
                          :when (contains? state s)
                          :let [v (get state s)]
                          [s' reducers] targets
                          :let [v' (reduce #(%2 %1 input) v reducers)]]
                      [s' v'])
                    (into {}))]
    (if (empty? state')
      reject-value
      state')))