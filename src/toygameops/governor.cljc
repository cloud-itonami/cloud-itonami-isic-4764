(ns toygameops.governor
  "ToyGameRetailGovernor -- the independent compliance layer that earns
  the ToyGameRetailAdvisor the right to commit. The advisor has no notion
  of whether a store is actually registered and license-verified,
  whether a named supply-order vendor is itself a registered/verified
  counterparty, whether its own proposed `:effect` secretly claims a
  direct actuation instead of a mere proposal, or whether it has
  silently drifted into a permanently out-of-scope decision area, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY
  (sales/inventory/return transaction logging, floor-staff scheduling,
  toy/game merchandise supply-order coordination, product-safety-concern
  flagging). It NEVER performs or authorizes:
    - setting or overriding a shelf/unit price
    - directly finalizing a product-recall-compliance decision (declaring
      a SKU recall-cleared, initiating/executing a recall, closing out a
      recall case)
    - directly finalizing an age-grading-safety decision (certifying/
      approving an age-grade or age-appropriate-use label as the
      OFFICIAL determination)

  Games/toys retail carries a direct child-product-safety dimension
  (choking/small-parts hazards, banned substances, age-grading label
  accuracy). This actor may FLAG a safety concern for a human -- it may
  NEVER itself be the system of record that finalizes a recall-
  compliance or age-grading-safety decision. That distinction is this
  governor's whole reason to exist.

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Store unverified           -- the target store record must exist
                                     AND be independently confirmed
                                     `:registered?`/`:verified?` in the
                                     store before ANY proposal for it may
                                     commit or even escalate. Never trusts
                                     a proposal's own claim about the
                                     store -- re-derived from the store's
                                     own record, the same 'ground truth,
                                     not self-report' discipline every
                                     sibling actor's governor uses.
    2. Vendor unverified          -- for `:coordinate-supply-order` ONLY,
                                     the proposal's own drafted `:value`
                                     must name a `:vendor-id` that
                                     resolves to an independently
                                     `:registered?`/`:verified?` vendor
                                     record. A missing vendor-id, or one
                                     that resolves to an unregistered or
                                     unverified vendor, is a HARD block --
                                     a supply-chain counterparty-
                                     verification gate that matters more
                                     than usual in this vertical, since an
                                     unverified toy/game import broker is
                                     exactly the channel through which
                                     unsafe or mislabeled stock enters a
                                     store.
    3. Effect not :propose        -- every proposal's `:effect` MUST be
                                     `:propose`. Any other effect value
                                     is, by construction, a claim to
                                     directly actuate/commit outside
                                     governance -- HARD block, not merely
                                     low-confidence.
    4. Scope exclusion            -- ANY proposal (regardless of op)
                                     whose op, summary, rationale, cites
                                     or draft value touches directly
                                     FINALIZING a recall-compliance
                                     decision or an age-grading-safety
                                     decision is a HARD, PERMANENT block
                                     -- this actor's charter excludes that
                                     territory structurally, not as a
                                     rollout milestone. Evaluated
                                     UNCONDITIONALLY on every proposal. An
                                     op outside the closed four-op
                                     allowlist is the SAME failure mode
                                     (an advisor proposing something it
                                     was never authorized to propose) and
                                     is folded into this same check.
                                     `:flag-safety-concern` itself is
                                     NEVER excluded by this check --
                                     surfacing a choking-hazard/small-
                                     parts/suspected-recall/age-grading-
                                     mismatch concern for a human is
                                     exactly this actor's job; only
                                     FINALIZING/certifying/executing a
                                     recall or age-grading decision is
                                     excluded (see `scope-excluded-terms`
                                     below -- phrased as the finalization/
                                     execution ACTION, never a bare noun
                                     like 'recall' or 'age grade' or
                                     'choking hazard', so the default mock
                                     advisor's own `:flag-safety-concern`
                                     rationale never self-trips this
                                     check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-safety-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `toygameops.phase` independently agrees:
      `:flag-safety-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one. This is a CHILD-PRODUCT-SAFETY
      op, so it must never be auto-commit-eligible at any phase, not even
      a hypothetical future phase 4 -- see `toygameops.phase`'s own
      structural invariant test.
    - A `:coordinate-supply-order` whose drafted `:value` names an
      `:estimated-cost` above `supply-cost-threshold` -- a large-value
      merchandise procurement proposal always needs a human sign-off,
      even when the governor and phase would otherwise allow
      auto-commit."
  (:require [clojure.string :as str]
            [toygameops.store :as store]))

(def confidence-floor 0.6)

(def supply-cost-threshold
  "Example single-store specialized games/toys procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-supply-order` proposal citing an
  `:estimated-cost` above this value ALWAYS escalates to human sign-off,
  regardless of confidence or rollout phase."
  750.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`). Note:
  NO op in this set directly finalizes a recall-compliance or
  age-grading-safety decision -- `:flag-safety-concern` only SURFACES a
  concern for human review, it never adjudicates one."
  #{:log-sales-record :schedule-staffing-operation
    :coordinate-supply-order :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  product-recall-compliance decision or an age-grading-safety decision,
  rather than merely flagging a concern for a human. Scanned across the
  proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'finalize the recall', 'certify the age grade'), never a
  bare noun like 'recall', 'choking hazard', 'age rating' or 'age grade'
  -- a bare noun would accidentally match inside this actor's own
  legitimate `:flag-safety-concern` default proposal text (whose whole
  job is to talk about choking hazards/small parts/suspected-recall
  stock/age-grading-label concerns) and self-block the happy path. See
  `toygameops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize the recall" "finalized the recall" "finalizing the recall"
   "initiate the recall" "initiated the recall" "initiating the recall"
   "execute the recall" "executed the recall" "executing the recall"
   "issue the recall notice" "issued the recall notice" "issuing the recall notice"
   "declare recall-compliant" "declared recall-compliant" "declaring recall-compliant"
   "certify recall compliance" "certified recall compliance" "certifying recall compliance"
   "confirm recall compliance" "confirmed recall compliance" "confirming recall compliance"
   "clear the product from recall" "cleared the product from recall" "clearing the product from recall"
   "close out the recall" "closed out the recall" "closing out the recall"
   "approve the age rating" "approved the age rating" "approving the age rating"
   "certify the age grade" "certified the age grade" "certifying the age grade"
   "finalize the age-grading determination" "finalized the age-grading determination" "finalizing the age-grading determination"
   "confirm the age-appropriate rating" "confirmed the age-appropriate rating" "confirming the age-appropriate rating"
   "assign the final age grade" "assigned the final age grade" "assigning the final age grade"
   "sign off on the recall" "signed off on the recall" "signing off on the recall"
   "sign off on the age rating" "signed off on the age rating" "signing off on the age rating"
   "certify the product safe for sale" "certified the product safe for sale" "certifying the product safe for sale"
   "リコールを実行" "リコールを実行した" "リコール対応を完了" "リコール対応を完了した"
   "リコールを確定" "リコールを確定した"
   "年齢区分を確定" "年齢区分を確定した" "年齢表示を確定" "年齢表示を確定した"
   "年齢区分を承認" "年齢区分を承認した"])

;; ----------------------------- checks -----------------------------

(defn- store-unverified-violations
  "The target store must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the proposal's
  own `:store-id` claim without a store lookup."
  [{:keys [store-id]} st]
  (let [s (store/store-record st store-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :store-unverified
        :detail (str store-id " は未登録または未検証の店舗 -- いかなる提案も進められない")}])))

(defn- vendor-unverified-violations
  "For `:coordinate-supply-order` ONLY, the proposal's own drafted
  `:value` must name a `:vendor-id` that resolves to an independently
  `:registered?`/`:verified?` vendor record. A missing vendor-id, or one
  that resolves to an unregistered/unverified vendor, is a HARD block --
  never trust the proposal's own vendor claim without a store lookup, the
  SAME 'ground truth, not self-report' discipline as
  `store-unverified-violations`, reapplied to the supply-chain
  counterparty."
  [proposal st]
  (when (= :coordinate-supply-order (:op proposal))
    (let [vendor-id (get-in proposal [:value :vendor-id])
          v (and vendor-id (store/vendor-record st vendor-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :vendor-unverified
          :detail (str (or vendor-id "(vendor-id missing)")
                        " は未登録または未検証の仕入先 -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a recall-compliance or
  age-grading-safety decision, regardless of confidence or how clean
  every other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "リコール確定・年齢区分確定など製品安全確定行為(recall-compliance / age-grading-safety finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-supply-order?
  "A `:coordinate-supply-order` proposal citing an `:estimated-cost` above
  `supply-cost-threshold` -- always needs human sign-off (SOFT escalate,
  not a hard block: the order itself is in scope, only its size requires
  a human)."
  [proposal]
  (and (= :coordinate-supply-order (:op proposal))
       (some-> proposal :value :estimated-cost (> supply-cost-threshold))))

(defn check
  "Censors a ToyGameRetailAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [store-id (or (:store-id proposal) (:store-id request))
        hard (into []
                   (concat (store-unverified-violations {:store-id store-id} store)
                           (vendor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-supply-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :store-id   (:store-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
