package com.anitech.scoremyday.enum_class

import com.anitech.scoremyday.R

enum class TaskIcon(val resId: Int) {
    BELL(R.drawable.ic_bell),
    LIST(R.drawable.ic_list),
    HOME(R.drawable.ic_home),
    HEART(R.drawable.ic_heart),
    DIAMOND(R.drawable.ic_diamond),
    LIGHT_BULB(R.drawable.ic_light_bulb),

    GIFT(R.drawable.ic_gift),
    CAKE(R.drawable.ic_cake),
    SHOPPING_CART(R.drawable.ic_shopping_cart),
    RESTAURANT(R.drawable.ic_restaurant),
    STEAMING_BOWL(R.drawable.ic_steaming_bowl),
    SHIELD(R.drawable.ic_shield),

    GOVERNMENT(R.drawable.ic_government),
    WALLET(R.drawable.ic_wallet),
    DOCUMENT(R.drawable.ic_document),
    GAME_CONTROLLER(R.drawable.ic_game_controller),
    AIRPLANE(R.drawable.ic_airplane),
    LUGGAGE(R.drawable.ic_luggage),

    TRAIN(R.drawable.ic_train),
    CAR(R.drawable.ic_car),
    ID_CARD(R.drawable.ic_id_card),
    GAME(R.drawable.ic_game),
    BASKETBALL(R.drawable.ic_basketball),
    TROPHY(R.drawable.ic_trophy),

    SPRINT(R.drawable.ic_sprint),
    SELF_CARE(R.drawable.ic_self_care),
    APARTMENT(R.drawable.ic_apartment),
    CASTLE(R.drawable.ic_castle),
    GRADUATION_CAP(R.drawable.ic_graduation_cap),
    BOOK(R.drawable.ic_book),

    PENCIL(R.drawable.ic_pencil),
    ALARM_CLOCK(R.drawable.ic_alarm_clock),
    HEALTH(R.drawable.ic_health),
    PEOPLE(R.drawable.ic_people),
    BABY(R.drawable.ic_baby),
    PAW_PRINT(R.drawable.ic_paw_print),

    LEAF(R.drawable.ic_leaf),
    PLANET(R.drawable.ic_planet),
    BOOKMARK(R.drawable.ic_bookmark),
    NEWSPAPER(R.drawable.ic_newspaper),
    WRENCH(R.drawable.ic_wrench),
    LIGHTNING_BOLT(R.drawable.ic_lightning_bolt);

    companion object {
        fun fromResId(resId: Int): TaskIcon? {
            return entries.find { it.resId == resId }
        }

        fun fromName(name: String): TaskIcon? {
            return entries.find { it.name == name }
        }
    }
}
