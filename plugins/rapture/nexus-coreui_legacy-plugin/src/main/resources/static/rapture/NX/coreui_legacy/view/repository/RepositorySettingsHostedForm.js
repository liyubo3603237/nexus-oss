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
 * Hosted repository "Settings" form.
 *
 * @since 3.0
 */
Ext.define('NX.coreui_legacy.view.repository.RepositorySettingsHostedForm', {
  extend: 'NX.coreui_legacy.view.repository.RepositorySettingsForm',
  alias: 'widget.nx-repository-settings-hosted-form',
  requires: [
    'NX.I18n'
  ],

  api: {
    submit: 'NX.direct.coreui_legacy_Repository.updateHosted'
  },
  settingsFormSuccessMessage: function(data) {
    return NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_UPDATE_SUCCESS') + data['id'];
  },

  initComponent: function() {
    var me = this;

    me.items = [
      { xtype: 'nx-coreui_legacy-repository-settings-localstorage' },
      {
        xtype: 'combo',
        name: 'writePolicy',
        fieldLabel: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_DEPLOYMENT'),
        helpText: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_DEPLOYMENT_HELP'),
        emptyText: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_DEPLOYMENT_PLACEHOLDER'),
        editable: false,
        store: [
          ['ALLOW_WRITE', NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_DEPLOYMENT_ALLOW_ITEM')],
          ['ALLOW_WRITE_ONCE', NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_DEPLOYMENT_DISABLE_ITEM')],
          ['READ_ONLY', NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_DEPLOYMENT_RO_ITEM')]
        ],
        queryMode: 'local'
      },
      {
        xtype: 'checkbox',
        name: 'browseable',
        fieldLabel: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_BROWSING'),
        value: true
      },
      {
        xtype: 'checkbox',
        name: 'exposed',
        fieldLabel: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_SETTINGS_PUBLISH'),
        value: true
      }
    ];

    me.callParent(arguments);
  }

});
