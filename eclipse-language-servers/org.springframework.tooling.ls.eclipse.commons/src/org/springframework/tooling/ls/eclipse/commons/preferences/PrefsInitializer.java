/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.preferences;

import static org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ALL_SERVERS;
import static org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ENABLE_BY_DEFAULT;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ServerInfo;

public class PrefsInitializer extends AbstractPreferenceInitializer {
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = LanguageServerPreferencesPage.getPrefsStoreFromPlugin();
		for (ServerInfo s : ALL_SERVERS) {
			store.setDefault(s.preferenceKey, ENABLE_BY_DEFAULT);
		}
		store.setDefault(PreferenceConstants.HIGHLIGHT_CODELENS_PREFS, false);
	}
}