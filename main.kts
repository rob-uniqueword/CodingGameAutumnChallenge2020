import java.util.*
import java.io.*
import java.math.*

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
fun main(args : Array<String>) {
    val input = Scanner(System.`in`)

    // game loop
    while (true) {

        val actions = mutableListOf<Action>();
        val players = mutableListOf<Player>();

        val actionCount = input.nextInt() // the number of spells and recipes in play
        for (i in 0 until actionCount) {
            val actionId = input.nextInt() // the unique ID of this spell or recipe
            val actionType = input.next() // in the first league: BREW; later: CAST, OPPONENT_CAST, LEARN, BREW
            val delta0 = input.nextInt() // tier-0 ingredient change
            val delta1 = input.nextInt() // tier-1 ingredient change
            val delta2 = input.nextInt() // tier-2 ingredient change
            val delta3 = input.nextInt() // tier-3 ingredient change
            val price = input.nextInt() // the price in rupees if this is a potion
            val tomeIndex = input.nextInt() // in the first two leagues: always 0; later: the index in the tome if this is a tome spell, equal to the read-ahead tax; For brews, this is the value of the current urgency bonus
            val taxCount = input.nextInt() // in the first two leagues: always 0; later: the amount of taxed tier-0 ingredients you gain from learning this spell; For brews, this is how many times you can still gain an urgency bonus
            val castable = input.nextInt() != 0 // in the first league: always 0; later: 1 if this is a castable player spell
            val repeatable = input.nextInt() != 0 // for the first two leagues: always 0; later: 1 if this is a repeatable player spell

            actions.add(Action(
                    actionId,
                    actionType,
                    arrayOf<Int>(delta0, delta1, delta2, delta3),
                    price,
                    tomeIndex,
                    taxCount,
                    castable,
                    repeatable))
        }

        for (i in 0 until 2) {
            val inv0 = input.nextInt() // tier-0 ingredients in inventory
            val inv1 = input.nextInt()
            val inv2 = input.nextInt()
            val inv3 = input.nextInt()
            val score = input.nextInt() // amount of rupees

            players.add(Player(
                    arrayOf<Int>(inv0, inv1, inv2, inv3),
                    score))
        }

        val bestAction = actions.filter { a -> canTakeAction(players[0], a) }.maxBy { a -> a.price }

        // Write an action using println()
        // To debug: System.err.println("Debug messages...");
        // in the first league: BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT
        if ( bestAction == null) {
            println("WAIT")
        } else {
            println("BREW " + bestAction.id)
        }
    }
}

fun canTakeAction(player:Player, action:Action) : Boolean {
    for (i in 0 until 4) {
        if (player.inventory[i] - action.delta[i] < 0) {
            return false
        }
    }
    return true
}

class Action (
        val id:Int,
        val actionType:String,
        val delta:Array<Int>,
        val price:Int,
        val tomeIndex:Int,
        val taxCount:Int,
        val castable:Boolean,
        val repeatable:Boolean
)

class Player (
        val inventory:Array<Int>,
        val score:Int
)