(ns psql.property-test
  "Property-based coverage for the most intricate string parsers in
  `psql.types`: range bound quoting/escaping, inet parsing, and array splitting."
  (:require [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [psql.types :as t]))

(def ^:private range->string @#'t/range->string)
(def ^:private read-range @#'t/read-range)
(def ^:private read-inet @#'t/read-inet)

;; ---- ranges ---------------------------------------------------------------

;; Bounds can be nil (unbounded), the empty string (a real empty value, which
;; must survive as "" not nil), or any printable text - including the comma,
;; brackets, quotes, backslash, and spaces that force quoting/escaping.
(def ^:private bound-gen
  (gen/one-of [(gen/return nil)
               (gen/return "")
               (gen/fmap #(apply str %)
                         (gen/vector (gen/elements (seq "ab, []()\"\\ 0")) 1 8))]))

(defspec range-string-roundtrip 300
  (prop/for-all [lower bound-gen
                 upper bound-gen
                 li? gen/boolean
                 ui? gen/boolean]
    (let [r (t/range lower upper :lower-inclusive? li? :upper-inclusive? ui?)]
      (= r (read-range (range->string r))))))

(defspec empty-range-roundtrips 1
  (prop/for-all [_ (gen/return nil)]
    (let [r (t/range nil nil :empty? true)]
      (= r (read-range (range->string r))))))

;; ---- inet -----------------------------------------------------------------

(def ^:private ipv4-gen
  (gen/fmap #(str/join "." %) (gen/vector (gen/choose 0 255) 4)))

(def ^:private ipv6-gen
  (gen/fmap #(str/join ":" %)
            (gen/vector (gen/fmap #(format "%x" %) (gen/choose 0 65535)) 3 8)))

(defspec inet-parses-address-and-optional-prefix 300
  (prop/for-all [addr (gen/one-of [ipv4-gen ipv6-gen])
                 pfx (gen/one-of [(gen/return nil) (gen/choose 0 128)])]
    (let [s (if pfx (str addr "/" pfx) addr)
          parsed (read-inet s)]
      (and (= addr (:address parsed))
           (= pfx (:prefix parsed))))))

;; ---- arrays ---------------------------------------------------------------

;; read-pg-array splits {a,b,c} on commas; it does not interpret quotes, so the
;; property holds for tokens that contain neither a comma nor a brace and have
;; no surrounding whitespace (which the splitter trims).
(def ^:private token-gen
  (gen/fmap #(apply str %)
            (gen/vector (gen/elements (seq "abc012_")) 1 6)))

(defspec array-splitting-preserves-simple-tokens 300
  (prop/for-all [tokens (gen/vector token-gen 1 8)]
    (= tokens (t/read-pg-array (str "{" (str/join "," tokens) "}")))))
