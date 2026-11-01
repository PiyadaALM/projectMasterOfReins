package game.combat;

import game.app.*;
import game.racer.*;
import java.awt.Color;
import java.awt.Graphics2D;

/** Placeholder effects can later be replaced by animation without changing combat rules. */
public final class CombatRenderer {
    private CombatRenderer() {}
    public static void draw(Graphics2D graphics,Racer racer) {
        Graphics2D g=(Graphics2D)graphics.create();
        CombatState state=racer.getCombat();
        double x=racer.getFeetBounds().getCenterX(),y=racer.getFeetBounds().getCenterY();
        if(state.getBoostRemaining()>0) {
            g.setColor(new Color(120,255,210,190));
            for(int i=0;i<3;i++) {
                double bx=x-racer.getFacingX()*(25+i*9),by=y-racer.getFacingY()*(25+i*9);
                g.drawLine((int)bx-5,(int)by-3,(int)bx+5,(int)by+3);
            }
        }
        g.dispose();
    }
}
