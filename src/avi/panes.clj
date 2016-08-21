(ns avi.panes
  (:require [clojure.spec :as s]))

(s/def ::nat (s/and int? (complement neg?)))
(s/def ::lens ::nat)
(s/def ::extent ::nat)
(s/def ::subtrees (s/coll-of ::tree :type vector?))
(s/def ::pane (s/keys :req [::lens]))
(s/def ::split (s/keys :req [::subtrees]))
(s/def ::tree (s/or :lens ::pane
                    :split ::split))
(s/def ::path (s/coll-of ::nat :type vector?))
(s/def ::shape (s/tuple (s/tuple ::nat ::nat)
                        (s/tuple ::nat ::nat)))

(defn- pane-area-shape
  "Shape of the rectangle where all panes are displayed.

  This accounts for the message line (ick)."
  [editor]
  (let [[rows cols] (get-in editor [:viewport :size])]
    [[0 0] [(dec rows) cols]]))

(defn panes
  "A transducer which visits all leaf nodes (panes) in a pane tree.

  The input pane trees must be augmented with ::shape and ::path, but only
  at the tree's root.  This information is used to augment each sub-pane
  before rf is applied to it."
  [rf]
  (fn
    ([] (rf))
    ([result] (rf result))
    ([result input]
     (if (::lens input)
       (rf result input)
       (let [{:keys [::subtrees ::shape ::path]} input]
         (reduce
           (fn [[result [[i j] [rows cols]] n] {:keys [::extent] :as input}]
             (let [height (or extent rows)
                   input (assoc input
                                ::shape [[i j] [height cols]]
                                ::path (conj path n))
                   result (rf result input)]
               [result [[(+ i height) j] [(- rows height) cols]] (inc n)]))
           [result shape 0]
           subtrees))))))

(defn panes-to-render
  [{:keys [::tree] :as editor}]
  (sequence panes [(assoc tree
                          ::shape (pane-area-shape editor)
                          ::path [])]))

(defn- shape
  [tree path [[i j] [rows cols] :as shape]]
  (if-not (empty? path)
    (let [[phead & prest] path
          {:keys [::extent] :as subnode} (first (::subtrees tree))]
      (if (zero? phead)
        (recur subnode prest [[i j] [(or extent rows) cols]])
        (recur
          (update tree ::subtrees (comp vec rest))
          (into [(dec phead)] prest)
          [[(+ i extent) j]
           [(- rows extent) cols]])))
    shape))

(defn current-pane-shape
  [{:keys [::tree ::path] :as editor}]
  (shape tree path (pane-area-shape editor)))

(defn- pane-lens-id
  [panes path]
  (if (empty? path)
    (::lens panes)
    (recur
      (get-in panes [::subtrees (first path)])
      (rest path))))

(defn current-pane-lens-id
  [{:keys [::tree ::path]}]
  (pane-lens-id tree path))

(s/fdef split-pane
  :args (s/cat :panes ::tree
               :path ::path
               :new-lens ::lens
               :total-height ::nat)
  :ret ::split)
(defn split-pane
  [panes path new-lens total-height]
  (let [panes (if (::lens panes)
                [panes]
                (::subtrees panes))
        each-pane-height (int (/ total-height (inc (count panes))))
        panes (-> (mapv #(assoc % ::extent each-pane-height) panes)
                (into [{::lens new-lens}]))]
    {::subtrees panes}))

(defn move-down-pane
  [editor]
  (assoc editor ::path [1]))
