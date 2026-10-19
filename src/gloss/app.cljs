(ns gloss.app
  "The application: the page for the current route, and the render loop."
  (:require [clojure.string :as str]
            [gloss.app.actions :as actions]
            [gloss.app.dom :as dom]
            [gloss.app.pages :as pages]
            [gloss.app.state :as state]
            [gloss.app.storage :as storage]
            [gloss.app.text :as text]
            [gloss.app.ui :as ui]))

(defn page
  "The whole page for the state's route."
  [s]
  (case (get-in s [:route :page])
    (:text :own) (if (state/current-doc s) (text/text-page s) (pages/mine-page s))
    :mine (pages/mine-page s)
    :published (pages/published-page s)
    :guide (pages/guide-page s)
    :about (pages/about-page s)
    :settings (pages/settings-page s)
    (pages/published-page s)))

(defn page-title
  "The window title for the state's route."
  [s]
  (let [route (:route s)]
    (if-let [doc (and (#{:text :own} (:page route)) (state/current-doc s))]
      (str (:title doc) " · " state/site-name)
      (if-let [[_ label] (some #(when (= (ui/section-of route) (first %)) %) state/sections)]
        (str label " · " state/site-name)
        state/site-name))))

;; ---------------------------------------------------------------------------
;; Rendering loop

;; Whether a render is under way, and whether the state changed during it.
(defonce ^:private rendering? (atom false))
(defonce ^:private dirty? (atom false))

(defn- focus-autofocus!
  "Focuses the element marked data-autofocus (a cell or source editor that
  has just opened), with the cursor at the end."
  []
  (when-let [el (.querySelector js/document "[data-autofocus]")]
    (when-not (= el (.-activeElement js/document))
      (.focus el)
      (when (.-setSelectionRange el)
        (let [n (count (.-value el))]
          (.setSelectionRange el n n))))))

(defn- apply-prefs!
  "Theme and text size are attributes on the root element, read by the CSS."
  [prefs]
  (let [root (.-documentElement js/document)
        {:keys [theme text-size]} (merge state/default-prefs prefs)]
    (if (= :system theme)
      (.removeAttribute root "data-theme")
      (.setAttribute root "data-theme" (name theme)))
    (.setAttribute root "data-text-size" (name text-size))))

(defn- show-crash!
  "Replaces a page that failed to render with one that can't fail."
  [container err]
  (js/console.error "Rendering failed:" err)
  (dom/clear! container)
  (dom/render! container (ui/crash-page (str (or (.-stack err) err))) actions/dispatch!)
  (set! (.-title js/document) state/site-name))

(defn- render!
  "Renders synchronously, so the page never lags behind typing. A state
  change during a render (e.g. a blur fired by removing an input) triggers
  one more pass instead of a nested render."
  []
  (if @rendering?
    (reset! dirty? true)
    (let [container (.getElementById js/document "app")]
      (reset! rendering? true)
      (try
        (loop []
          (reset! dirty? false)
          (dom/render! container (page @state/state) actions/dispatch!)
          (set! (.-title js/document) (page-title @state/state))
          (apply-prefs! (:prefs @state/state))
          (when @dirty? (recur)))
        (focus-autofocus!)
        (catch :default err (show-crash! container err))
        (finally (reset! rendering? false))))))

(def ^:private saved-keys
  "The parts of the state that are saved."
  [:docs :order :overrides :view :text-views :prefs])

(def ^:dynamic ^:private *adopting?*
  "True while taking in another tab's save, which mustn't be saved back."
  false)

(defn- adopt-other-tab!
  "Another tab saved: show its data here, so that this tab's next save
  doesn't overwrite it. This tab's waiting save and undo history (which
  refer to the old data) are dropped."
  []
  (when-let [saved (try (storage/load-saved) (catch :default _ nil))]
    (storage/drop-pending!)
    (binding [*adopting?* true]
      (swap! state/state #(-> %
                        (merge (select-keys saved saved-keys))
                        (assoc :history {})
                        (assoc-in [:status :synced?] true))))))

(defn- start-state
  "The saved state, or a fresh one. Saved data that can't be read is copied
  aside first, so the fresh state's saves can't destroy it."
  []
  (let [loaded (try {:state (storage/load-saved)} (catch :default e {:error e}))]
    (if-let [e (:error loaded)]
      (do (js/console.error "Reading saved data failed:" e)
          (assoc-in (state/initial-state) [:status :load-backup] (or (storage/back-up-saved!) "(no copy could be made)")))
      (or (:state loaded) (state/initial-state)))))

(defn init
  "Starts the site once the page has loaded: the saved state, the page for
  the address, the listeners for keys, navigation, saving and other tabs,
  and the first render. From then on, every change of state renders the
  page again, and saves if it changed what is saved."
  []
  (reset! state/state (let [s (start-state)]
                   (assoc s :route (state/route-of (.-hash js/location))
                            :mode (get-in s [:prefs :open-mode] :read))))
  ;; Ctrl/⌘+Z and Ctrl/⌘+Shift+Z (or Ctrl+Y) undo and redo changes to the
  ;; text in Edit mode. Inside a field, the field's own undo applies.
  (.addEventListener js/window "keydown"
                     (fn [e]
                       (let [k (str/lower-case (.-key e))
                             mod? (or (.-ctrlKey e) (.-metaKey e))
                             tag (.. e -target -tagName)]
                         (when (and mod? (= :edit (:mode @state/state))
                                    (not (#{"INPUT" "TEXTAREA" "SELECT"} tag))
                                    (#{"z" "y"} k))
                           (.preventDefault e)
                           (actions/dispatch! e (if (or (= k "y") (.-shiftKey e)) [:history/redo] [:history/undo]))))))
  (.addEventListener js/window "hashchange"
                     (fn [_]
                       ;; Pages open for reading, unless the action that
                       ;; navigated asked for another mode (New opens in Edit).
                       (swap! state/state #(assoc % :route (state/route-of (.-hash js/location))
                                            :mode (or (:pending-mode %) (get-in % [:prefs :open-mode]) :read)
                                            :pending-mode nil
                                            :note-highlight #{}
                                            :ui {}))
                       (.scrollTo js/window 0 0)))
  ;; Saves wait a moment; a page that is being left writes at once.
  (.addEventListener js/window "pagehide" storage/flush!)
  (.addEventListener js/document "visibilitychange"
                     #(when (= "hidden" (.-visibilityState js/document)) (storage/flush!)))
  ;; Another tab or window saved.
  (.addEventListener js/window "storage"
                     #(when (= storage/storage-key (.-key %)) (adopt-other-tab!)))
  (add-watch state/state ::render (fn [_ _ old new]
                              (when (and (not *adopting?*)
                                         (not= (select-keys old saved-keys) (select-keys new saved-keys)))
                                (storage/save! new))
                              (render!)))
  (render!))

(defonce ^:private boot
  (when (exists? js/document)
    (if (= "loading" (.-readyState js/document))
      (.addEventListener js/document "DOMContentLoaded" init)
      (init))))
