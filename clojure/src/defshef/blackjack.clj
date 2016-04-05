(ns defshef.blackjack
  (:require [expectations :refer [expect in]]))

;; Modelling the base data

(def suits [:H :C :D :S])
(def ranks [2 3 4 5 6 7 8 9 10 :J :Q :K :A])

(defn card
  "build a card representation"
  [rank suit]
  {:pre [(some #{rank} ranks)
         (some #{suit} suits)]}
  {:rank rank
   :suit suit})

(expect 2 (:rank (card 2 :H)))
(expect :A (:rank (card :A :H)))
(expect :H (:suit (card 2 :H)))
(expect AssertionError (card 11 :H))
(expect AssertionError (card 7 :T))

(defn cards
  "Helper function for building a sequence of cards

  Usage: (cards 2 :H, 2 :C, 3 :S)
  "
  [& ranks-and-suits]
  (->> ranks-and-suits
       (partition 2)
       (mapv #(apply card %))))

(expect [(card 2 :H) (card :K :S)] (cards 2 :H, :K :S))
(expect [(card 2 :H) (card :K :S) (card :A :H)] (cards 2 :H, :K :S, :A :H))

;; Calculate hand value

(defn- card-value
  "How much is a card worth?

  In this function aces are always hard"
  [{:keys [rank] :as card}]
  (condp #(%1 %2) rank
    number? rank
    #{:J :Q :K} 10
    #{:A} 11
    (throw (ex-info "Unknown rank" {:card card}))))

(expect 5 (card-value (card 5 :C)))
(expect 10 (card-value (card 10 :H)))
(expect 10 (card-value (card :K :S)))
(expect 10 (card-value (card :Q :C)))
(expect 10 (card-value (card :J :D)))
(expect 11 (card-value (card :A :S)))
(expect Throwable (card-value {:rank :x}))

(def ^:private sum (partial reduce +))
(defn- has-ace [hand]
  (some #(= :A (:rank %)) hand))

(defn value
  "How much is a hand worth?"
  [hand]
  (let [total (sum (map card-value hand))
        ace (has-ace hand)
        qualifier (cond (> total 21) :bust
                        (and (= 21 total) (= 2 (count hand))) :blackjack
                        ace :soft
                        :else nil)]
    ; bust with an ace? Use lower value instead
    (if (and ace (= qualifier :bust))
      [(- total 10) :hard]
      [total qualifier])))

(expect [4 nil] (value (cards 2 :H, 2 :C)))
(expect [8 nil] (value (cards 2 :H, 2 :C, 2 :D, 2 :S)))
(expect [19 nil] (value (cards 2 :H, 2 :C, 7 :D, 8 :S)))
(expect [21 nil] (value (cards :K :H, 5 :C, 6 :S)))
(expect [23 :bust] (value (cards :K :H, :Q :C, 3 :S)))
(expect [27 :bust] (value (cards :J :H, 8 :C, 9 :S)))
(expect [21 :blackjack] (value (cards :K :H, :A :C)))
(expect [21 :blackjack] (value (cards :A :H, 10 :S)))
(expect [16 :soft] (value (cards :A :S, 5 :H)))
(expect [13 :hard] (value (cards :A :S, 5 :H, 7 :H)))
(expect [12 :hard] (value (cards :A :S, :A :H)))

;; Shuffle and deal

(def fresh-deck (for [s suits n ranks] (card n s)))

(expect (card 2 :H) (in fresh-deck))
(expect (card 6 :S) (in fresh-deck))
(expect (card :Q :D) (in fresh-deck))
(expect (card :A :C) (in fresh-deck))

(defn deal
  "Start a new game"
  []
  (let [deck (shuffle fresh-deck)
        [a b c d & deck] deck]
    {:deck deck
     :dealer [b d]
     :player [a c]}))

(expect {:deck (drop 4 fresh-deck)
         :dealer (cards 3 :H, 5 :H)
         :player (cards 2 :H, 4 :H)}
        (with-redefs [shuffle identity] (deal)))

(let [stacked-deck (cards :A :H, 7 :S, :Q :H, 6 :D, :K :S, :J :C)]
  (expect {:deck (cards :K :S, :J :C)
           :dealer (cards 7 :S, 6 :D)
           :player (cards :A :H, :Q :H)}
          (with-redefs [shuffle (constantly stacked-deck)] (deal))))

(def ^:private card-faces
  {2 "🂢" 3 "🂣" 4 "🂤" 5 "🂥" 6 "🂦" 7 "🂧" 8 "🂨" 9 "🂩" 10 "🂪"
   :J "🂫" :Q "🂭" :K "🂮" :A "🂡"})
(def ^:private suit-offset
  {:S 0 :H 16 :D 32 :C 48})
(defn- adjust-suit [[x y :as face] suit]
  (str x (char (+ (int y) (suit-offset suit)))))

(defn- render-card
  "Render a single card"
  ([] "🂠 ")
  ([{:keys [rank suit]}]
   (str (adjust-suit (card-faces rank) suit) " ")))

(expect "🂠 " (render-card))
(expect "🂡 " (render-card (card :A :S)))

(defn- render-dealer
  "Render the dealer's hand"
  [hand]
  (apply str
         (render-card)
         (map render-card (drop 1 hand))))

(defn- render-player
  "Render the player's hand"
  [hand]
  (let [[total qualifier] (value hand)]
    (str (apply str (map render-card hand))
         " "
         (cond
           (= :blackjack qualifier) "Blackjack!"
           qualifier (str (name qualifier) " " total)
           :else total))))

(defn render
  "Print out the game state neatly"
  [{:keys [dealer player]}]
  (str "Dealer: " (render-dealer dealer) "\n"
       "Player: " (render-player player)))

(expect
  (str "Dealer: 🂠 🃉 " "\n"
       "Player: 🂾 🂥  15")
  (render {:deck (cards :K :S, 6 :D)
           :dealer (cards 7 :S, 9 :D)
           :player (cards :K :H, 5 :S)}))

(expect
  (str "Dealer: 🂠 🃛 " "\n"
       "Player: 🂱 🃕  soft 16")
  (render {:deck (cards :K :S, 6 :D)
           :dealer (cards 7 :S, :J :C)
           :player (cards :A :H, 5 :C)}))

(expect
  (str "Dealer: 🂠 🃛 " "\n"
       "Player: 🂱 🃊  Blackjack!")
  (render {:deck (cards :K :S, 6 :D)
           :dealer (cards 7 :S, :J :C)
           :player (cards :A :H, 10 :D)}))
