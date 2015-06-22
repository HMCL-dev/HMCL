/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.settings;

/**
 *
 * @author hyh
 */
public class Op extends PlayerList<Op.Operator> {

    @Override
    protected Op.Operator newPlayer(String name) {
	return new Op.Operator(name);
    }

    public static class Operator extends PlayerList.BasePlayer {

	public int level;

	public Operator(String name) {
	    super(name);
	    level = 4;
	}
    }
}
