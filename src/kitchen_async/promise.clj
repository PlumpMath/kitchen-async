(ns kitchen-async.promise
  (:refer-clojure :exclude [promise let])
  (:require [clojure.core :as cc]
            [clojure.spec :as s]))

(defmacro promise [[resolve reject] & body]
  (cc/let [bindings (cond-> []
                      resolve (conj resolve '&resolve)
                      reject (conj reject '&reject))]
    `(js/Promise.
       (fn [~'&resolve ~'&reject]
         (cc/let ~bindings
           ~@body)))))

(defmacro resolved [x]
  `(~'&resolve ~x))

(defmacro rejected [x]
  `(~'&reject ~x))

(defmacro let [bindings & body]
  ((reduce (fn [f [name expr]]
             (cc/let [p (f expr)]
               (fn [e]
                 `(then ~p (fn [~name] (->promise ~e))))))
           identity
           (partition 2 bindings))
   `(do ~@body)))

(defmacro plet [[name expr] & body]
  `(then (js/Promise.all ~expr)
         (fn [~name]~@body)))

(declare catch)

(def ^:private ^:dynamic *env* nil)

(defn- fixup-alias [env sym]
  (or (when-let [ns-name (some-> (namespace sym) symbol)]
        (when-let [ns (or (get-in env [:ns :require-macros ns-name])
                          (get-in env [:ns :require ns-name]))]
          (symbol (name ns) (name sym))))
      sym))

(s/def ::error-type symbol?)
(s/def ::error-name simple-symbol?)
(s/def ::catch-op
  (s/and symbol?
         (fn [sym]
           (when-let [sym (fixup-alias *env* sym)]
             (or (= sym 'catch) (= sym 'kitchen-async.promise/catch))))))
(s/def ::non-catch-expr
  (s/and seq?
         (s/cat :op #(not (s/valid? ::catch-op %))
                :args (s/* any?))))
(s/def ::try-body
  (s/* (s/alt :simple-expr (complement seq?)
              :non-catch-expr ::non-catch-expr)))
(s/def ::catch-clause
  (s/and seq?
         (s/cat :catch ::catch-op
                :error-type ::error-type
                :error-name ::error-name
                :catch-body (s/* any?))))
(s/def ::try-args
  (s/cat :try-body ::try-body
         :catch-clauses (s/* ::catch-clause)))

(defmacro try [& body]
  (binding [*env* &env]
    (cc/let [result (s/conform ::try-args body)]
      (if (= result ::s/invalid)
        (cc/let [ed (s/explain-data ::try-args body)
                 msg (with-out-str
                       (println "Call to" 'kitchen-async.promise/try "did not conform to spec:")
                       (s/explain-out ed))]
          (throw (ex-info msg ed)))
        (cc/let [{:keys [try-body catch-clauses]} result
                 try-body (s/unform ::try-body try-body)]
          (reduce (fn [p {:keys [error-type error-name catch-body]}]
                    `(catch* ~p (fn [~error-name]
                                  (if (instance? ~error-type ~error-name)
                                    (do ~@catch-body)
                                    (reject ~error-name)))))
                  `(do ~@try-body)
                  catch-clauses))))))
