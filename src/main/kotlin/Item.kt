package org.example

import kotlinx.serialization.SerialName

enum class Item {
    @SerialName("nature")
    NATURE,
    @SerialName("mobility")
    MOBILITY,
    @SerialName("energy_building")
    ENERGY_BUILDING,
    @SerialName("community")
    COMMUNITY,
    @SerialName("circular_economy")
    CIRCULAR_ECONOMY,
    @SerialName("local_consumption")
    LOCAL_CONSUMPTION
}