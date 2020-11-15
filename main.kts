import java.util.*
import java.io.*
import java.math.*
import kotlin.math.abs

val INVENTORY_SIZE = 10
val IDEAL_INVENTORY = intArrayOf(4,2,2,2)
val LOOK_FORWARD_TURNS = 8
val TURN_PRICE = 5

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
                    intArrayOf(delta0, delta1, delta2, delta3),
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
                    intArrayOf(inv0, inv1, inv2, inv3),
                    score))
        }

        val me = players[0]
        val potions = actions.filter { a -> a.actionType == "BREW" }
        val mySpells = actions.filter { a -> a.actionType == "CAST" }
        val theirSpells = actions.filter { a -> a.actionType == "OPPONENT_CAST" }

        // Write an action using println()
        // To debug: System.err.println("Debug messages...");
        // in the first league: BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT

        val nearbyPotions = getNearbyPotions(me, mySpells, potions, LOOK_FORWARD_TURNS)
        val bestPotionPath = nearbyPotions.maxBy { it.key.price - it.value.size * TURN_PRICE }

        System.err.println("=== Inventory ===")
        System.err.println(me.inventory.joinToString())
        System.err.println("=== Nearby Potions ===")
        System.err.println(nearbyPotions.entries.joinToString("\n"))
        System.err.println("=== Best Potion ===")
        System.err.println(bestPotionPath)

        if ( bestPotionPath == null ) {
            val bestSpell = getBestSpell(me, mySpells)
            when {
                bestSpell == null -> println("REST")
                else -> { println("CAST " + bestSpell.id) }
            }
        } else {
            when {
                bestPotionPath.value.isEmpty() -> println("BREW " + bestPotionPath.key.id)
                bestPotionPath.value[0] == null -> println("REST")
                bestPotionPath.value[0] != null -> println("CAST " + bestPotionPath.value[0]!!.id)
            }
        }
    }
}

fun getBestSpell(player:Player, spells:List<Action>) : Action? {
    val currentDistance = player.inventory.minusMerge(IDEAL_INVENTORY).sumBy { i -> abs(i) }

    return spells.filter { s -> player.canTakeAction(s) }
            .map { s -> Pair(s, player.inventory.merge(s.delta).minusMerge(IDEAL_INVENTORY).sumBy { i -> abs(i) }) }
            .filter { p -> p.second <= currentDistance }
            .minBy { p -> p.second }?.first
}

fun getNearbyPotions(player:Player, spells:List<Action>, potions:List<Action>, steps:Int) : Map<Action,List<Action?>> {
    val castableSpells = spells.map { s -> Action(s.id, s.actionType, s.delta, s.price, s.tomeIndex, s.taxCount, true, s.repeatable) }.toSet()
    var states = setOf<State>(State(listOf<Action>(), spells.toSet(), player))

    val possiblePotions = mutableMapOf<Action, List<Action?>>()

    for (i in 0 until steps) {
        //System.err.println("=== STATES ===")
        //System.err.println(states)

        // if we can make a new potion add it to the list

        states.forEach {
            s -> potions.filter { p -> s.player.canTakeAction(p) }.forEach { p -> possiblePotions.putIfAbsent(p, s.castSpells) }
        }

        // if not try casting some spells or resting

        states = states.flatMap { state ->
            val newStates = state.availableSpells.filter { spell -> state.player.canTakeAction(spell) }.map {
                spell -> State(state.castSpells.plus(spell), state.availableSpells.minus(spell), Player(state.player.inventory.merge(spell.delta),0))
            }.toMutableList()

            if (state.availableSpells.isEmpty() || state.availableSpells.size < castableSpells.size || state.availableSpells.any { !it.castable } ) {
                newStates.add(State(state.castSpells.plusElement(null), castableSpells, state.player))
            }

            newStates.toList()
        }.toSet()
    }

    return possiblePotions
}

class State(
        val castSpells:List<Action?>,
        val availableSpells:Set<Action>,
        val player:Player
) {
    override fun toString(): String {
        return "\nInventory: " + player.inventory.joinToString() + "\nAvailableSpells: " + availableSpells + "\nCastSpells: " + castSpells + "\n"
    }
}

fun Player.canTakeAction(action:Action) : Boolean {
    when (action.actionType) {
        "CAST" -> {
            if (!action.castable) {
                return false
            }
        }
        "OPPONENT_CAST" -> {
            return false
        }
    }

    val result = inventory.merge(action.delta)

    if (result.any { i -> i < 0 }) {
        return false
    }

    if (result.sum() > INVENTORY_SIZE) {
        return false
    }

    return true
}

fun IntArray.minusMerge(other:IntArray) : IntArray {
    if (other.size != this.size) {
        throw InputMismatchException("Attempting to merge IntArrays of different lengths")
    }

    return this.zip(other).map { (a,b) -> a - b }.toIntArray()
}

fun IntArray.merge(other:IntArray) : IntArray {
    if (other.size != this.size) {
        throw InputMismatchException("Attempting to merge IntArrays of different lengths")
    }

    return this.zip(other).map { (a,b) -> a + b }.toIntArray()
}

class Action (
        val id:Int,
        val actionType:String,
        val delta:IntArray,
        val price:Int,
        val tomeIndex:Int,
        val taxCount:Int,
        val castable:Boolean,
        val repeatable:Boolean
) {
    override fun toString(): String {
        return id.toString() + ":[" + delta.joinToString() + "]"
    }
}

class Player (
        val inventory:IntArray,
        val score:Int
)