/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
/*global Ext, NX*/

/**
 * Local Storage repository settings fields.
 *
 * @since 3.0
 */
Ext.define('NX.coreui_legacy.view.repository.RepositorySettingsLocalStorage', {
  extend: 'Ext.form.FieldContainer',
  alias: 'widget.nx-coreui_legacy-repository-settings-localstorage',
  requires: [
    'NX.I18n'
  ],

  defaults: {
    xtype: 'textfield',
    allowBlank: true
  },

  items: [
    {
      name: 'defaultLocalStorageUrl',
      fieldLabel: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_LOCAL'),
      helpText: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_LOCAL_HELP'),
      readOnly: true,
      submitValue: false
    },
    {
      name: 'overrideLocalStorageUrl',
      fieldLabel: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_OVERRIDE'),
      helpText: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_OVERRIDE_HELP'),
      emptyText: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_OVERRIDE_PLACEHOLDER')
    }
  ]

});
