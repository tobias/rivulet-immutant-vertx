(ns rivulet.state)

(defn remember! [state key items]
  (swap! state update-in [key] concat (if (coll? items) items [items])))

(defn recall [state key]
  (get @state key))

(defn forget! [state key]
  (let [v (recall state key)]
    (swap! state dissoc key)
    v))
