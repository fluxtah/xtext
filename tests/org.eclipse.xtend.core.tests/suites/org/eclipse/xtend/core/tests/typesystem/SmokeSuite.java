/*******************************************************************************
 * Copyright (c) 2013 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtend.core.tests.typesystem;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 */
@RunWith(Suite.class)
@SuiteClasses({
	SkipLastCharacters2.class,
	SkipFirstCharacters2.class,
	SkipCharacterInBetween2.class,
	SkipTokensInBetween2.class,
	SkipNodesInBetween2.class,
	SkipLastCharacters.class,
	SkipFirstCharacters.class,
	SkipCharacterInBetween.class,
	SkipTokensInBetween.class,
	SkipNodesInBetween.class
})
public class SmokeSuite {

}