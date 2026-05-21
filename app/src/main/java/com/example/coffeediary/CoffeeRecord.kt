package com.example.coffeediary

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coffee_records")
data class CoffeeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,      // 咖啡名称
    val date: String,      // 日期 "YYYY-MM-DD"
    val photo: String,     // Base64 照片数据
    val temp: String,      // 温度: "hot" | "ice" | "warm"
    val sugar: String      // 糖度: "none" | "half" | "full"
)
