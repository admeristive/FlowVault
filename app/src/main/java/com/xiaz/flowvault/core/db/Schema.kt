package com.xiaz.flowvault.core.db

/**
 * 表结构唯一定义处。三条列口径全库统一：
 * 时间戳一律 UTC epoch 毫秒，日分组一律本地 day_key('yyyy-MM-dd')，金额一律带符号「分」。
 */
internal object Schema {

    /** 每次加表/改表 +1，同时写进 DbOpenHelper.CURRENT_VERSION。 */
    const val VERSION: Int = 2

    val CREATE_TABLES: List<String> = listOf(

        SETTINGS,
        NOVELS,
        TODOS,
        POMODORO_SESSIONS,
        LEDGER_CATEGORIES,
        CAPTURES,
        LEDGER_TX,
        FLASH_DECKS,
        FLASH_CARDS,
        GATE_ATTEMPTS,
        TIMELINE_EVENTS,
        NOVEL_BOOKMARKS,
    )

    /**
     * 每一级版本要补的语句。新建走 CREATE_TABLES、老库升级走这张表，两处共用同一批字符串常量，
     * 不会出现"新装机有这列、老库升出来没这列"的岔路。缺哪一级就硬失败，绝不静默跳版。
     */
    val MIGRATIONS: Map<Int, List<String>> = mapOf(
        2 to listOf(NOVEL_BOOKMARKS, Indexes.IX_MARK_NOVEL),
    )

    private const val SETTINGS = """
        CREATE TABLE settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    """

    private const val NOVELS = """
        CREATE TABLE novels (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            display_name TEXT NOT NULL UNIQUE,
            saf_uri TEXT NOT NULL,
            file_size INTEGER,
            char_count INTEGER,
            encoding TEXT NOT NULL DEFAULT 'UTF-8',
            last_char_offset INTEGER NOT NULL DEFAULT 0,
            last_opened_at INTEGER,
            imported_at INTEGER NOT NULL
        )
    """

    private const val TODOS = """
        CREATE TABLE todos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            note TEXT,
            priority INTEGER NOT NULL DEFAULT 1,
            day_key TEXT NOT NULL,
            start_min INTEGER,
            end_min INTEGER,
            status INTEGER NOT NULL DEFAULT 0,
            planned_pomos INTEGER NOT NULL DEFAULT 0,
            done_pomos INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            finished_at INTEGER
        )
    """

    private const val POMODORO_SESSIONS = """
        CREATE TABLE pomodoro_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            todo_id INTEGER REFERENCES todos(id) ON DELETE SET NULL,
            kind INTEGER NOT NULL,
            planned_sec INTEGER NOT NULL,
            elapsed_sec INTEGER NOT NULL DEFAULT 0,
            started_at INTEGER NOT NULL,
            ended_at INTEGER,
            interrupt_count INTEGER NOT NULL DEFAULT 0,
            fuse_triggered INTEGER NOT NULL DEFAULT 0
        )
    """

    private const val LEDGER_CATEGORIES = """
        CREATE TABLE ledger_categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            sort INTEGER NOT NULL DEFAULT 0
        )
    """

    private const val CAPTURES = """
        CREATE TABLE captures (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            raw_text TEXT NOT NULL,
            route INTEGER NOT NULL,
            ref_table TEXT,
            ref_id INTEGER,
            novel_id INTEGER REFERENCES novels(id) ON DELETE SET NULL,
            novel_offset INTEGER,
            created_at INTEGER NOT NULL,
            day_key TEXT NOT NULL
        )
    """

    private const val LEDGER_TX = """
        CREATE TABLE ledger_tx (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            amount_cents INTEGER NOT NULL,
            category_id INTEGER REFERENCES ledger_categories(id) ON DELETE SET NULL,
            note TEXT,
            occurred_at INTEGER NOT NULL,
            day_key TEXT NOT NULL,
            capture_id INTEGER REFERENCES captures(id) ON DELETE SET NULL
        )
    """

    private const val FLASH_DECKS = """
        CREATE TABLE flash_decks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            source_path TEXT,
            card_count INTEGER NOT NULL DEFAULT 0,
            imported_at INTEGER NOT NULL
        )
    """

    private const val FLASH_CARDS = """
        CREATE TABLE flash_cards (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            deck_id INTEGER NOT NULL REFERENCES flash_decks(id) ON DELETE CASCADE,
            front TEXT NOT NULL,
            back TEXT NOT NULL,
            box INTEGER NOT NULL DEFAULT 1,
            last_review_at INTEGER,
            due_at INTEGER,
            correct_count INTEGER NOT NULL DEFAULT 0,
            wrong_count INTEGER NOT NULL DEFAULT 0
        )
    """

    private const val GATE_ATTEMPTS = """
        CREATE TABLE gate_attempts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            context INTEGER NOT NULL,
            asked INTEGER NOT NULL,
            correct INTEGER NOT NULL,
            passed INTEGER NOT NULL,
            granted_sec INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        )
    """

    private const val TIMELINE_EVENTS = """
        CREATE TABLE timeline_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts INTEGER NOT NULL,
            day_key TEXT NOT NULL,
            module TEXT NOT NULL,
            action TEXT NOT NULL,
            ref_table TEXT,
            ref_id INTEGER,
            payload TEXT
        )
    """

    /** 书签只存**字符偏移**，不存原文片段——正文换了编码或用户重导一次，偏移照样有效。 */
    private const val NOVEL_BOOKMARKS = """
        CREATE TABLE novel_bookmarks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            novel_id INTEGER NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
            char_offset INTEGER NOT NULL,
            label TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )
    """
}

internal object Indexes {
    const val IX_MARK_NOVEL =
        "CREATE INDEX IF NOT EXISTS ix_mark_novel ON novel_bookmarks(novel_id, char_offset)"

    val CREATE: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS ix_todo_day ON todos(day_key, status, start_min)",
        "CREATE INDEX IF NOT EXISTS ix_ledger_day ON ledger_tx(day_key, occurred_at)",
        "CREATE INDEX IF NOT EXISTS ix_ledger_cat ON ledger_tx(category_id, occurred_at)",
        "CREATE INDEX IF NOT EXISTS ix_capture_day ON captures(day_key, created_at)",
        "CREATE INDEX IF NOT EXISTS ix_card_deck ON flash_cards(deck_id)",
        "CREATE INDEX IF NOT EXISTS ix_card_due ON flash_cards(due_at)",
        "CREATE INDEX IF NOT EXISTS ix_pomo_todo ON pomodoro_sessions(todo_id, started_at)",
        "CREATE INDEX IF NOT EXISTS ix_tl_day ON timeline_events(day_key, ts)",
        "CREATE INDEX IF NOT EXISTS ix_tl_mod ON timeline_events(module, ts)",
        IX_MARK_NOVEL,
    )
}
