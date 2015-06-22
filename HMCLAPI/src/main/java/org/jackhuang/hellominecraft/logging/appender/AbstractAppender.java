/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging.appender;

import java.io.Serializable;
import org.jackhuang.hellominecraft.logging.layout.ILayout;

/**
 *
 * @author hyh
 */
public abstract class AbstractAppender implements IAppender {

    String name;
    private final ILayout<? extends Serializable> layout;
    private final boolean ignoreExceptions;

    public AbstractAppender(String name, ILayout<? extends Serializable> layout) {
	this(name, layout, true);
    }

    public AbstractAppender(String name, ILayout<? extends Serializable> layout, boolean ignoreExceptions) {
	this.name = name;
	this.layout = layout;
	this.ignoreExceptions = ignoreExceptions;
    }

    @Override
    public String getName() {
	return name;
    }

    @Override
    public boolean ignoreExceptions() {
	return ignoreExceptions;
    }

    @Override
    public ILayout<? extends Serializable> getLayout() {
	return this.layout;
    }
}
