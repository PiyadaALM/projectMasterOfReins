package game.combat;



/** Combat intent is separate from walking input. */
public record CombatAction(boolean attack,boolean guard) {
    public static final CombatAction NONE=new CombatAction(false,false);
}
