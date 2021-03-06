(ns kitchen-async.promise
  (:require-macros [kitchen-async.promise :as p]
                   [cljs.core.async.macros :refer [go]])
  (:require [clojure.core.async :as a]
            [clojure.core.async.impl.channels :refer [ManyToManyChannel]]))

(defn resolve [x]
  (js/Promise.resolve x))

(defn reject [x]
  (js/Promise.reject x))

(defn then [p f]
  (.then p f))

(defn catch* [p f]
  (.catch p f))

(defprotocol Promisable
  (->promise* [this]))

(extend-protocol Promisable
  js/Promise
  (->promise* [p] p)

  ManyToManyChannel
  (->promise* [c]
    (p/promise [resolve reject]
      (go
        (let [x (a/<! c)]
          (if (instance? js/Error x)
            (reject x)
            (resolve x))))))

  default
  (->promise* [x]
    (resolve x)))

(defn ->promise [x]
  (->promise* x))

(defn callback->promise [f & args]
  (p/promise [resolve reject]
    (letfn [(callback [err val]
              (if err
                (reject err)
                (resolve val)))]
      (apply f (concat args [callback])))))
