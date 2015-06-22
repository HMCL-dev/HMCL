/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.logging.message;

public class ObjectMessage
        implements IMessage {

    private static final long serialVersionUID = -5903272448334166185L;
    private transient Object obj;

    public ObjectMessage(Object obj) {
        if (obj == null) {
            obj = "null";
        }
        this.obj = obj;
    }

    @Override
    public String getFormattedMessage() {
        return this.obj.toString();
    }

    @Override
    public String getFormat() {
        return this.obj.toString();
    }

    @Override
    public Object[] getParameters() {
        return new Object[]{this.obj};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }

        ObjectMessage that = (ObjectMessage) o;

        return this.obj != null ? this.obj.equals(that.obj) : that.obj == null;
    }

    @Override
    public int hashCode() {
        return this.obj != null ? this.obj.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ObjectMessage[obj=" + this.obj.toString() + "]";
    }

    @Override
    public Throwable getThrowable() {
        return (this.obj instanceof Throwable) ? (Throwable) this.obj : null;
    }
}
