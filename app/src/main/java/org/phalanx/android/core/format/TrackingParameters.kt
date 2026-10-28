package org.phalanx.android.core.format

/**
 * Query parameters that only say who shared a link or which ad, mail or app it came from. A name
 * that changes what the page shows is never listed: `ref` is a GitHub branch, `t` a YouTube start
 * time, `psc` an Amazon variant, `unlocked_article_code` a gifted article. Affiliate tags stay too:
 * they pay the person sharing, and say nothing about the person reading. Names are lowercase.
 */
internal object TrackingParameters {
    /** Removed from every link: ad click ids and mail campaign tags no page needs to render. */
    private val everywhere = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "gad_source", "msclkid", "yclid", "ysclid",
        "twclid", "ttclid", "li_fat_id", "epik", "igshid", "igsh", "srsltid", "_ga", "_gl", "_kx",
        "mc_cid", "mc_eid", "mkt_tok", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp", "hsctatracking",
        "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "wickedid", "rb_clickid", "s_cid",
        "ml_subscriber", "ml_subscriber_hash", "_openstat",
        "pk_campaign", "pk_kwd", "pk_keyword", "pk_source", "pk_medium", "pk_content", "pk_cid",
        "mtm_campaign", "mtm_kwd", "mtm_source", "mtm_medium", "mtm_content", "mtm_cid", "mtm_group",
        "mtm_placement"
    )

    /** Whole families: Google Analytics campaigns and HubSpot ads. */
    private val prefixes = arrayOf("utm_", "hsa_")

    private val google = setOf(
        "ved", "ei", "sei", "sca_esv", "sca_upv", "gs_lcrp", "gs_lp", "gs_lcp", "sclient", "oq", "aqs",
        "sourceid", "rlz", "uact", "bih", "biw", "iflsig", "sxsrf", "client"
    )
    private val amazon = setOf(
        "pd_rd_r", "pd_rd_w", "pd_rd_wg", "pd_rd_i", "pf_rd_p", "pf_rd_r", "pf_rd_s", "pf_rd_t", "pf_rd_i",
        "pf_rd_m", "content-id", "ref", "ref_", "qid", "sr", "crid", "sprefix", "dib", "dib_tag", "_encoding"
    )
    private val ebay = setOf(
        "_trkparms", "_trksid", "amdata", "mkcid", "mkevt", "mkrid", "campid", "toolid", "customid"
    )
    private val aliexpress = setOf(
        "spm", "scm", "pvid", "algo_pvid", "algo_exp_id", "btsid", "ws_ab_test", "gatewayadapt", "pdp_npi", "pdp_ext_f"
    )
    private val twitter = setOf("s", "t", "ref_src", "ref_url")
    private val youtube = setOf("si", "feature", "pp")

    // Declared before [bySite], which reads it while the object initialises.
    private val countries = listOf(
        "com", "fr", "de", "co.uk", "es", "it", "nl", "be", "com.be", "ch", "at", "pl", "pt", "se", "ie", "ca",
        "com.au", "co.jp", "in", "co.in", "com.br", "com.mx"
    )

    /** Only safe on these sites and their subdomains, where the name is known to be a tracker. */
    private val bySite: Map<String, Set<String>> = buildMap {
        put("youtube.com", youtube)
        put("youtu.be", youtube)
        put("spotify.com", setOf("si"))
        put("twitter.com", twitter)
        put("x.com", twitter)
        put("facebook.com", setOf("mibextid", "__cft__", "__tn__", "rdid", "sfnsn"))
        put("threads.net", setOf("xmt", "slof"))
        put("threads.com", setOf("xmt", "slof"))
        put(
            "tiktok.com",
            setOf(
                "_r", "_t", "is_from_webapp", "sender_device", "is_copy_url", "share_app_id", "share_item_id",
                "share_link_id", "social_sharing", "tt_from", "u_code", "sec_user_id"
            )
        )
        put("reddit.com", setOf("share_id", "rdt", "ref", "ref_source", "ref_campaign", "correlation_id"))
        put("linkedin.com", setOf("trk", "trkinfo", "trackingid", "lipi", "refid", "midtoken", "midsig"))
        put("bing.com", setOf("form", "cvid", "qs", "sp", "sc", "sk", "ghsh", "ghacc", "ghpl", "pq", "lq"))
        put("substack.com", setOf("r"))
        put("medium.com", setOf("source"))
        put("quora.com", setOf("ch", "oid", "share", "srid", "target_type"))
        put("steampowered.com", setOf("snr"))
        put("steamcommunity.com", setOf("snr"))
        put("twitch.tv", setOf("tt_content", "tt_medium"))
        put("nytimes.com", setOf("smid", "smtyp"))
        put("theguardian.com", setOf("cmp"))
        put("lemonde.fr", setOf("lmd_medium", "lmd_campaign", "lmd_creation", "lmd_source"))
        countries.forEach { country ->
            put("google.$country", google)
            put("amazon.$country", amazon)
            put("ebay.$country", ebay)
        }
        put("aliexpress.com", aliexpress)
        put("aliexpress.us", aliexpress)
    }

    /** Whether the parameter [name], already lowercase, tracks on a site whose rules are [site]. */
    fun isTracking(name: String, site: Set<String>?): Boolean =
        name in everywhere || prefixes.any { name.startsWith(it) } || site?.contains(name) == true

    /** The rules of [host] (lowercase), found by its own name or that of any domain it is under. */
    fun forHost(host: String): Set<String>? {
        var start = 0
        while (true) {
            bySite[host.substring(start)]?.let { return it }
            val dot = host.indexOf('.', start)
            if (dot < 0) return null
            start = dot + 1
        }
    }
}
