/**
 * Back 2 Browser Bytecode Translator
 * Copyright (C) 2012 Jaroslav Tulach <jaroslav.tulach@apidesign.org>
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
package org.apidesign.vm4brwsr;

import java.io.IOException;
import java.io.InputStream;

/** Generator of JavaScript from bytecode of classes on classpath of the VM.
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
class VM extends ByteCodeToJavaScript {
    public VM(Appendable out) {
        super(out);
    }

    private VM(Appendable out, ObfuscationDelegate obfuscationDelegate) {
        super(out, obfuscationDelegate);
    }

    static {
        // uses VMLazy to load dynamic classes
        boolean assertsOn = false;
        assert assertsOn = true;
        if (assertsOn) {
            VMLazy.init();
            Zips.init();
        }
    }

    @Override
    boolean debug(String msg) throws IOException {
        return false;
    }
    
    static void compile(Bck2Brwsr.Resources l, Appendable out, StringArray names) throws IOException {
        new VM(out).doCompile(l, names);
    }

    static void compile(Bck2Brwsr.Resources l, Appendable out, StringArray names,
                        ObfuscationDelegate obfuscationDelegate) throws IOException {
        new VM(out, obfuscationDelegate).doCompile(l, names);
    }

    protected void doCompile(Bck2Brwsr.Resources l, StringArray names) throws IOException {
        append("(function VM(global) {var fillInVMSkeleton = function(vm) {");
        StringArray processed = new StringArray();
        StringArray initCode = new StringArray();
        StringArray skipClass = new StringArray();
        for (String baseClass : names.toArray()) {
            references.add(baseClass);
            for (;;) {
                String name = null;
                for (String n : references.toArray()) {
                    if (skipClass.contains(n)) {
                        continue;
                    }
                    if (processed.contains(n)) {
                        continue;
                    }
                    name = n;
                }
                if (name == null) {
                    break;
                }
                InputStream is = loadClass(l, name);
                if (is == null) {
                    lazyReference(this, name);
                    skipClass.add(name);
                    continue;
                }
                try {
                    String ic = compile(is);
                    processed.add(name);
                    initCode.add(ic == null ? "" : ic);
                } catch (RuntimeException ex) {
                    throw new IOException("Error while compiling " + name + "\n", ex);
                }
            }

            for (String resource : scripts.toArray()) {
                while (resource.startsWith("/")) {
                    resource = resource.substring(1);
                }
                InputStream emul = l.get(resource);
                if (emul == null) {
                    throw new IOException("Can't find " + resource);
                }
                readResource(emul, this);
            }
            scripts = new StringArray();
            
            StringArray toInit = StringArray.asList(references.toArray());
            toInit.reverse();

            for (String ic : toInit.toArray()) {
                int indx = processed.indexOf(ic);
                if (indx >= 0) {
                    final String theCode = initCode.toArray()[indx];
                    if (!theCode.isEmpty()) {
                        append(theCode).append("\n");
                    }
                    initCode.toArray()[indx] = "";
                }
            }
        }
        append(
              "  return vm;\n"
            + "  };\n"
            + "  function mangleClass(name) {\n"
            + "    return name.replace__Ljava_lang_String_2Ljava_lang_CharSequence_2Ljava_lang_CharSequence_2(\n"
            + "      '_', '_1').replace__Ljava_lang_String_2CC('.','_');\n"
            + "  };\n"
            + "  global.bck2brwsr = function() {\n"
            + "    var args = Array.prototype.slice.apply(arguments);\n"
            + "    var vm = fillInVMSkeleton({});\n"
            + "    var loader = {};\n"
            + "    loader.vm = vm;\n"
            + "    loader.loadClass = function(name) {\n"
            + "      var attr = mangleClass(name);\n"
            + "      var fn = vm[attr];\n"
            + "      if (fn) return fn(false);\n"
            + "      return vm.org_apidesign_vm4brwsr_VMLazy(false).\n"
            + "        load__Ljava_lang_Object_2Ljava_lang_Object_2Ljava_lang_String_2_3Ljava_lang_Object_2(loader, name, args);\n"
            + "    }\n"
            + "    if (vm.loadClass) {\n"
            + "      throw 'Cannot initialize the bck2brwsr VM twice!';\n"
            + "    }\n"
            + "    vm.loadClass = loader.loadClass;\n"
            + "    vm._reload = function(name, byteCode) {;\n"
            + "      var attr = mangleClass(name);\n"
            + "      delete vm[attr];\n"
            + "      return vm.org_apidesign_vm4brwsr_VMLazy(false).\n"
            + "        reload__Ljava_lang_Object_2Ljava_lang_Object_2Ljava_lang_String_2_3Ljava_lang_Object_2_3B(loader, name, args, byteCode);\n"
            + "    };\n"
            + "    vm.loadBytes = function(name, skip) {\n"
            + "      return vm.org_apidesign_vm4brwsr_VMLazy(false).\n"
            + "        loadBytes___3BLjava_lang_Object_2Ljava_lang_String_2_3Ljava_lang_Object_2I(loader, name, args, typeof skip == 'number' ? skip : 0);\n"
            + "    }\n"
            + "    vm.java_lang_reflect_Array(false);\n"
            + "    vm.org_apidesign_vm4brwsr_VMLazy(false).\n"
            + "      loadBytes___3BLjava_lang_Object_2Ljava_lang_String_2_3Ljava_lang_Object_2I(loader, null, args, 0);\n"
            + "    return loader;\n"
            + "  };\n");
        append("}(this));");
    }
    private static void readResource(InputStream emul, Appendable out) throws IOException {
        try {
            int state = 0;
            for (;;) {
                int ch = emul.read();
                if (ch == -1) {
                    break;
                }
                if (ch < 0 || ch > 255) {
                    throw new IOException("Invalid char in emulation " + ch);
                }
                switch (state) {
                    case 0: 
                        if (ch == '/') {
                            state = 1;
                        } else {
                            out.append((char)ch);
                        }
                        break;
                    case 1:
                        if (ch == '*') {
                            state = 2;
                        } else {
                            out.append('/').append((char)ch);
                            state = 0;
                        }
                        break;
                    case 2:
                        if (ch == '*') {
                            state = 3;
                        }
                        break;
                    case 3:
                        if (ch == '/') {
                            state = 0;
                        } else {
                            state = 2;
                        }
                        break;
                }
            }
        } finally {
            emul.close();
        }
    }

    private static InputStream loadClass(Bck2Brwsr.Resources l, String name) throws IOException {
        return l.get(name + ".class"); // NOI18N
    }

    static String toString(String name) throws IOException {
        StringBuilder sb = new StringBuilder();
//        compile(sb, name);
        return sb.toString().toString();
    }

    private StringArray scripts = new StringArray();
    private StringArray references = new StringArray();
    
    @Override
    protected boolean requireReference(String cn) {
        if (references.contains(cn)) {
            return false;
        }
        references.add(cn);
        return true;
    }

    @Override
    protected void requireScript(String resourcePath) {
        scripts.add(resourcePath);
    }

    @Override
    String assignClass(String className) {
        return "vm." + className + " = ";
    }
    
    @Override
    String accessClass(String className) {
        return "vm." + className;
    }

    @Override
    String getVMObject() {
        return "vm";
    }
    
    private static void lazyReference(Appendable out, String n) throws IOException {
        String cls = n.replace('/', '_');
        String dot = n.replace('/', '.');
        
        out.append("\nvm.").append(cls).append(" = function() {");
        out.append("\n  var instance = arguments.length == 0 || arguments[0] === true;");
        out.append("\n  delete vm.").append(cls).append(";");
        out.append("\n  var c = vm.loadClass('").append(dot).append("');");
        out.append("\n  return vm.").append(cls).append("(instance);");
        out.append("\n}");
    }
}
