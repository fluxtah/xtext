/**
 * Copyright (c) 2015 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.xtext.psi.stubindex;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.eclipse.xtext.psi.impl.BaseXtextFile;

@SuppressWarnings("all")
public class ExportedObjectQualifiedNameIndex extends StringStubIndexExtension<BaseXtextFile> {
  public static StubIndexKey<String, BaseXtextFile> KEY = StubIndexKey.<String, BaseXtextFile>createIndexKey("ExportedObjectQualifiedNameIndex");
  
  @Override
  public StubIndexKey<String, BaseXtextFile> getKey() {
    return ExportedObjectQualifiedNameIndex.KEY;
  }
}
