package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.properties;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import org.jackhuang.hellominecraft.utils.code.Base64;

public class Property {

    public final String name;
    public final String value;
    public final String signature;

    public Property(String value, String name) {
	this(value, name, null);
    }

    public Property(String name, String value, String signature) {
	this.name = name;
	this.value = value;
	this.signature = signature;
    }

    public boolean isSignatureValid(PublicKey publicKey) {
	try {
	    Signature sign = Signature.getInstance("SHA1withRSA");
	    sign.initVerify(publicKey);
	    sign.update(this.value.getBytes());
	    return sign.verify(Base64.decode(this.signature.toCharArray()));
	} catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
	    e.printStackTrace();
	}
	return false;
    }
}
