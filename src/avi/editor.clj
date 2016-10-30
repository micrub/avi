(ns avi.editor
 "Functions (including basse responders, middleware, and utilties) for
    manipulating the editor map."
 (:import (java.io FileNotFoundException))
 (:require [clojure.spec :as s]
           [clojure.set :as set]
           [packthread.core :refer :all]
           [packthread.lenses :as l]
           [avi.pervasive :refer :all]
           [avi.beep :as beep]
           [avi.documents]
           [avi.edit-context :as ec]
           [avi.edit-context
             [lines :as lines]]
           [avi.layout :as layout]
           [avi.layout.panes :as p]
           [avi.lenses]
           [avi.world :as w]))

(s/def ::mode keyword?)
(s/def ::editor
  (s/merge
    (s/keys :req [::mode])
    :avi.documents/editor
    ::p/editor))

;; -- Initial state ----------------------------------------------------------

(defn initial-editor
  [[lines columns] [filename]]
  {::mode :normal
   :avi.documents/documents [(avi.documents/load filename)]
   :lenses {0 #:avi.lenses{:document 0
                           :viewport-top 0
                           :point [0 0]
                           :last-explicit-j 0}}
   ::p/tree {:avi.layout.panes/lens 0}
   ::p/path []
   ::layout/shape [[0 0] [lines columns]]
   :beep? false})

;; -- Building middlewares ---------------------------------------------------

(defn keystroke-middleware
  [keystroke a-fn]
  (fn [handler]
    (fn [editor event]
      (if (= event [:keystroke keystroke])
        (a-fn editor)
        (handler editor event)))))

;; -- Tracking the current lens & document -----------------------------------

(defn current-lens-path
  [editor]
  [:lenses (::p/lens (p/current-pane editor))])

(defn current-lens
  [editor]
  (get-in editor (current-lens-path editor)))

(defn current-document-path
  [editor]
  [:avi.documents/documents (:avi.lenses/document (current-lens editor))])

(s/fdef edit-context
  :args (s/cat :editor ::editor
               :new-context (s/? any?))
  :ret ::editor)
(let [document-keys #{:avi.documents/lines
                      :avi.documents/undo-log
                      :avi.documents/redo-log
                      :avi.documents/in-transaction?}
      computed-keys #{:viewport-height}
      lens-keys #{:avi.lenses/viewport-top :avi.lenses/point :avi.lenses/last-explicit-j}]
  (def edit-context
    "Perform some action in an \"edit context\".

    An \"edit context\" is the minimal information from a document and a lens,
    combined in such a way that a function can make edits to the file and move
    the cursor and viewport.
    
    This is intended to be used with packthread's \"in\" macro, like so:

      (+> editor
        (in e/edit-context
          (assoc :foo :bar)))"
    (beep/add-beep-to-focus
      (fn edit-context*
        ([editor]
         (merge
           (-> editor
               (get-in (current-document-path editor))
               (select-keys document-keys))
           (-> (current-lens editor)
               (select-keys lens-keys))
           (let [[_ [height _]] (::layout/shape (p/current-pane editor))]
             {:viewport-height (dec height)})))
        ([editor new-context]
         (-> editor
           (update-in (current-document-path editor) merge (select-keys new-context document-keys))
           (update-in (current-lens-path editor) merge (select-keys new-context lens-keys))))))))

;; -- Modes ------------------------------------------------------------------

(defn enter-normal-mode
  [editor]
  (assoc editor :avi.editor/mode :normal :message nil))

(defn mode-middleware
  [mode mode-responder]
  (fn [responder]
    (fn [editor event]
      (if (= mode (:avi.editor/mode editor))
        (mode-responder editor event)
        (responder editor event)))))

;; -- Terminal resizing ------------------------------------------------------

(defn wrap-handle-resize
  [responder]
  (fn [editor [event-type size :as event]]
    (if (= event-type :resize)
      (+> editor
        (assoc-in [::layout/shape 1] size)
        (in edit-context
          ec/adjust-viewport-to-contain-point))
      (responder editor event))))

;; -- Exceptions and failures ------------------------------------------------

(defn wrap-handle-exceptions
  [responder]
  (fn [editor event]
    (try
      (responder editor event)
      (catch Throwable e
        (merge editor (ex-data e))))))

;; ---------------------------------------------------------------------------
