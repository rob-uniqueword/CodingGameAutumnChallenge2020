import java.util.*
import java.io.*
import java.math.*
import kotlin.math.abs

val INVENTORY_SIZE = 10
val IDEAL_INVENTORY = intArrayOf(4,2,2,2)
val LOOK_FORWARD_TURNS = 5
val TURN_PRICE = 5
val INGREDIENT_VALUES = intArrayOf(1,2,2,2)

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
        val potions = actions.filter { a -> a.actionType == "BREW" }.also { l -> l.forEachIndexed { i,a -> a.index = i } }
        val mySpells = actions.filter { a -> a.actionType == "CAST" }.also { l -> l.forEachIndexed { i,a -> a.index = i } }
        val theirSpells = actions.filter { a -> a.actionType == "OPPONENT_CAST" }.also { l -> l.forEachIndexed { i,a -> a.index = i } }
        val learnableSpells = actions.filter { a -> a.actionType == "LEARN" }.also { l -> l.forEachIndexed { i,a -> a.index = i } }

        // Write an action using println()
        // To debug: System.err.println("Debug messages...");
        // in the first league: BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT
        println(getBestAction(me, mySpells, potions, learnableSpells))
    }
}

fun getBestAction(me:Player, mySpells:List<Action>, potions:List<Action>, learnableSpells:List<Action>) : String {
    val nearbyPotions = getNearbyPotions(me, mySpells, potions, LOOK_FORWARD_TURNS)

    System.err.println("=== Nearby Potions ===")
    System.err.println(nearbyPotions.entries.joinToString("\n"))

    if (!nearbyPotions.isEmpty()) {
        val bestPotionPath = nearbyPotions.maxBy { it.key.price - it.value.size * TURN_PRICE }!!
        when {
            bestPotionPath.value.isEmpty() -> return "BREW " + bestPotionPath.key.id
            bestPotionPath.value[0] == null -> return "REST"
            bestPotionPath.value[0] != null -> return "CAST " + bestPotionPath.value[0]!!.id
        }
    }

    val bestSpellToLearn = getBestSpellToLearn( me, mySpells, learnableSpells )
    if (bestSpellToLearn != null) {
        return "LEARN " + bestSpellToLearn.id
    }

    val bestSpellToCast = getBestSpellToCast(me, mySpells)
    if (bestSpellToCast != null) {
        return "CAST " + bestSpellToCast.id
    }

    return "REST"
}

fun getBestSpellToLearn(player:Player, playerSpells:List<Action>, learnableSpells:List<Action>) : Action? {
    val bestPlayerSpellValue = playerSpells
            .map { s -> Pair(s, s.delta.withIndex().sumBy { (i,n) -> n * INGREDIENT_VALUES[i] } ) }
            .maxBy { (s,n) -> n }!!
            .second

    val availableSpellsWithScore = learnableSpells
            .filter { s -> player.canTakeAction(s) }
            .map { s -> Pair(s, s.delta.withIndex().sumBy { (i,n) -> n * INGREDIENT_VALUES[i] } + s.taxCount) }

    System.err.println("=== Learnable Spells ===")
    System.err.println(availableSpellsWithScore.joinToString("\n"))

    return availableSpellsWithScore
            .filter { (s,v) -> v > bestPlayerSpellValue }
            .maxBy { (s,v) -> v }
            ?.first
}

fun getBestSpellToCast(player:Player, spells:List<Action>) : Action? {
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
        "BREW" -> return inventory.merge(action.delta).isViableInventory()
        "CAST" -> return action.castable && inventory.merge(action.delta).isViableInventory()
        "LEARN" -> {
            return inventory[0] >= action.index!!
        }
        else -> { return false }
    }
}

fun IntArray.isViableInventory() : Boolean = all { it >= 0 } && sum() <= INVENTORY_SIZE

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
        val repeatable:Boolean,
        var index:Int? = null
) {
    override fun toString(): String {
        return id.toString() + ":[" + delta.joinToString() + "]"
    }
}

class Player (
        val inventory:IntArray,
        val score:Int
)