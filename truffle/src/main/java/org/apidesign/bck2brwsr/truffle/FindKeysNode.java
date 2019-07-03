/**
 * Back 2 Browser Bytecode Translator
 * Copyright (C) 2012-2017 Jaroslav Tulach <jaroslav.tulach@apidesign.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://opensource.org/licenses/GPL-2.0.
 */
package org.apidesign.bck2brwsr.truffle;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;


final class FindKeysNode extends Node {
    @Child
    private Node prototype;

    @Child
    private InteropLibrary keys = InteropLibrary.getFactory().createDispatched(3);

    FindKeysNode(boolean prototype) {
        this.prototype = prototype ? Message.READ.createNode() : null;
    }



    final String findKey(TruffleObject js, String shortName) {
        String underscoreName = shortName + "__";
        try {
            if (prototype != null) {
                js = (TruffleObject) ForeignAccess.sendRead(prototype, js, "__proto__");
            }

            Object jsKeys = keys.getMembers(js);
            long jsSize = keys.getArraySize(jsKeys);
            for (int i = 0; i < jsSize; i++) {
                Object n = keys.readArrayElement(jsKeys, i);
                if (n instanceof String && ((String) n).startsWith(underscoreName)) {
                    return (String) n;
                }
            }
        } catch (InteropException ex) {
            throw ex.raise();
        }
        throw UnknownIdentifierException.raise(shortName);
    }

    static void unwrapArgs(Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ClassObject) {
                args[i] = ((ClassObject)args[i]).jsClass;
            } else if (args[i] instanceof JavaObject) {
                args[i] = ((JavaObject)args[i]).jsObj;
            }
        }
    }
}