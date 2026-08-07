/**
 * AppDatabase - Room 数据库单例
 *
 * 功能：
 * - 持有 generation_history 表，提供 HistoryDao 访问入口
 * - 通过 companion 双重检查锁实现进程内单例，避免重复构建开销
 *
 * 迁移策略：显式 Migration，不使用 fallbackToDestructiveMigration，
 * 避免静默丢弃用户历史数据（与 LocalDream 保持一致的破坏性变更显式失败策略）。
 * - version 1: 初始表结构
 * - version 2: generation_history 新增 modelId 列（MIGRATION_1_2）
 * - version 3: generation_history 新增 favorite 列（MIGRATION_2_3）
 * - version 4: generation_history 新增 runOnCpu、useOpenCL 列（MIGRATION_3_4）
 */
package com.qihao.open.rwkv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HistoryEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    /** 暴露历史记录 Dao */
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2：新增 modelId 列。
         * Room 对非空 String 列要求 DEFAULT 值，旧行统一落空串表示“历史模型未知”。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE generation_history ADD COLUMN modelId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v2 -> v3：新增 favorite 列，纯加列不破坏现有数据。
         * SQLite 的 BOOLEAN 以 INTEGER 存储，0=false 1=true，DEFAULT 0 兼容旧行。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE generation_history ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v3 -> v4：新增 runOnCpu、useOpenCL 列，纯加列不破坏现有数据。
         * 默认 0 兼容旧行：旧数据视为 NPU 推理（runOnCpu=0）。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE generation_history ADD COLUMN runOnCpu INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE generation_history ADD COLUMN useOpenCL INTEGER NOT NULL DEFAULT 0"
                )
                // 与 Entity 声明的 Index(value = ["modelId"]) 对齐：v3 库无此索引，迁移时补建，
                // 否则 Room schema 校验因 Expected 有 modelId 索引、Found 无而抛 IllegalStateException
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_generation_history_modelId ON generation_history(modelId)"
                )
            }
        }

        /** 获取数据库单例，使用 applicationContext 避免 Activity 泄漏 */
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "magicwx_history.db",
            )
                // 显式注册迁移：未来 schema 升级缺 Migration 时在 open 阶段显式失败，
                // 而非静默清空用户历史
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { INSTANCE = it }
        }
    }
}
