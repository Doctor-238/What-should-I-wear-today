package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class StyleWithItems(
    @Embedded val style: SavedStyle,

    // [수정 완료] Room이 길을 잃지 않도록, 연결고리의 정보를 명확하게 알려줍니다.
    @Relation(
        parentColumn = "styleId",      // 부모 테이블(SavedStyle)의 연결 컬럼
        entityColumn = "id",          // 자식 테이블(ClothingItem)의 연결 컬럼
        associateBy = Junction(
            value = StyleItemCrossRef::class,
            parentColumn = "styleId",     // 연결 테이블에서 부모를 가리키는 컬럼
            entityColumn = "clothingId"  // 연결 테이블에서 자식을 가리키는 컬럼
        )
    )
    val items: List<ClothingItem>
)
