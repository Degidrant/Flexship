package com.flexship.flexshipcookingass.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stages_table")
data class Stages(
    @PrimaryKey (autoGenerate = true) var id: Int = 0,
    val name: String ="",
    val time: Int =0,
    val dishId:Int =0
)
