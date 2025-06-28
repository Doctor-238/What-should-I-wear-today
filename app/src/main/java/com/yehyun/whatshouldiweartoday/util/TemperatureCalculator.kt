package com.yehyun.whatshouldiweartoday.util

import kotlin.math.roundToInt

object TemperatureCalculator {

    // --- 여기 있는 값들을 수정하여 계산식을 바꿀 수 있습니다 ---

    /**
     * 기본 기준이 되는 온도입니다.
     * 옷을 아무것도 입지 않았을 때 쾌적하다고 느끼는 온도를 기준으로 설정하는 것이 좋습니다.
     */
    private const val BASE_TEMPERATURE = 28.0

    /**
     * 두께 점수(1~10)가 1 증가할 때마다 온도를 얼마나 내릴지에 대한 값입니다.
     * 음수 값으로 설정해야 점수가 높을수록(두꺼울수록) 적정 온도가 내려갑니다.
     */
    private const val THICKNESS_WEIGHT = -2.5

    /**
     * 기장 점수(0~20)가 1 증가할 때마다 온도를 얼마나 내릴지에 대한 값입니다.
     * 음수 값으로 설정해야 점수가 높을수록(길수록) 적정 온도가 내려갑니다.
     */
    private const val LENGTH_WEIGHT = -0.6

    /**
     * 옷의 종류별로 적용할 최종 온도 보정 값입니다.
     * 예를 들어, 같은 재질이라도 상의보다는 아우터가 더 추운 날씨에 입으므로 온도를 더 낮게 보정합니다.
     */
    private val CATEGORY_MODIFIERS = mapOf(
        "아우터" to -7, // 아우터는 다른 옷 위에 입으므로, 큰 폭으로 온도를 낮춥니다.
        "상의" to -2,   // 상의는 하의보다 추위를 더 민감하게 느끼는 상체에 입으므로, 조금 더 낮춥니다.
        "하의" to -1,   // 하의는 일반적인 기준으로, 약간만 낮춥니다.
        "신발" to 0,   // 신발, 가방, 모자, 기타는 핵심 체온에 큰 영향을 주지 않으므로 보정하지 않습니다.
        "가방" to 0,
        "모자" to 0,
        "기타" to 0
    )
    // ---------------------------------------------------------


    /**
     * 기장, 두께, 종류를 바탕으로 최종 적정 온도를 계산합니다.
     * @param lengthScore 기장 점수 (0-20)
     * @param thicknessScore 두께 점수 (1-10)
     * @param category 옷 종류 ("상의", "하의" 등)
     * @return 계산된 최종 적정 온도 (정수)
     */
    fun calculate(lengthScore: Int, thicknessScore: Int, category: String): Int {

        // 1. 두께에 따른 온도 보정치 계산 (두께 점수 1은 보정 없음)
        val thicknessAdjustment = (thicknessScore - 1) * THICKNESS_WEIGHT

        // 2. 기장에 따른 온도 보정치 계산
        val lengthAdjustment = lengthScore * LENGTH_WEIGHT

        // 3. 종류에 따른 최종 보정치 가져오기 (map에 없는 종류면 0)
        val categoryModifier = CATEGORY_MODIFIERS.getOrDefault(category, 0)

        // 4. 모든 값을 합산하여 최종 온도 계산
        val finalTemperature = BASE_TEMPERATURE + thicknessAdjustment + lengthAdjustment + categoryModifier

        // 최종 값을 반올림하여 정수로 반환
        return finalTemperature.roundToInt()
    }
}