(ns avi.render
  (:import [java.util Arrays])
  (:require [clojure.set :refer [map-invert]]
            [avi.editor :as e]
            [avi.edit-context :as ec]
            [avi.color :as color]))

(defmulti ^:private point-position :mode)

(defmethod point-position :default
  [editor]
  (let [edit-context (e/edit-context editor)
        [edit-context-point-i edit-context-point-j] (:point edit-context)
        viewport-top (:viewport-top edit-context)]
    [(- edit-context-point-i viewport-top) edit-context-point-j]))

(defmethod point-position :command-line
  [editor]
  (let [[height] (:size (:viewport editor))]
    [(dec height) (inc (count (:command-line editor)))]))

(let [byte-array-class (Class/forName "[B")]
  (defn byte-array?
    [obj]
    (= byte-array-class (class obj))))

(defn fill-rendition-line!
  [{:keys [width] rendered-chars :chars, rendered-attrs :attrs} i [attrs text]]
  (.getChars text 0 (min width (count text)) rendered-chars (* i width))
  (if (byte-array? attrs)
    (System/arraycopy attrs 0 rendered-attrs (* i width) (min width (count attrs)))
    (Arrays/fill rendered-attrs (* i width) (* (inc i) width) attrs)))

(defn render-pane!
  [editor rendition [from-line to-line] lens-number]
  (let [document (get-in editor (e/current-document-path editor))]
    (doseq [i (range from-line to-line)]
      (let [edit-context (e/edit-context editor)
            top (:viewport-top edit-context)
            edit-context-line (+ i top)
            edit-context-line-count (ec/line-count edit-context)]
        (if (< edit-context-line edit-context-line-count)
          (fill-rendition-line! rendition i [(color/make :white :black) (ec/line edit-context edit-context-line)])
          (fill-rendition-line! rendition i [(color/make :blue :black) "~"]))))
    (fill-rendition-line! rendition to-line [(color/make :black :white) (or (:name document) "[No Name]")])))

(defn render-message-line!
  [editor rendition]
  (let [[height] (:size (:viewport editor))
        i (dec height)]
    (cond
      (and (:prompt editor) (:command-line editor))
      (fill-rendition-line! rendition i [(color/make :white :black) (str (:prompt editor) (:command-line editor))])

      (:message editor)
      (let [[foreground background text] (:message editor)]
        (fill-rendition-line! rendition i [(color/make foreground background) text])))))

(defn render
  [editor]
  (let [[height width] (:size (:viewport editor))
        default-attrs (color/make :white :black)
        rendered-chars (char-array (* height width) \space)
        rendered-attrs (byte-array (* height width) default-attrs)
        rendition {:width width
                   :chars rendered-chars
                   :attrs rendered-attrs
                   :point (point-position editor)}]
    (render-pane! editor rendition [0 (- height 2)] 0)
    (render-message-line! editor rendition)
    rendition))

(defn rendered
  [editor]
  (assoc editor :rendition (render editor)))

(defn wrap
  [responder]
  (fn [editor event]
    (-> editor
      (responder event)
      rendered)))
