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
 * Add hosted repository window.
 *
 * @since 3.0
 */
Ext.define('NX.coreui_legacy.view.repository.RepositoryAddHosted', {
  extend: 'NX.coreui_legacy.view.repository.RepositoryAdd',
  alias: 'widget.nx-repository-add-hosted',
  requires: [
    'NX.I18n'
  ],

  initComponent: function() {
    var me = this;

    me.items = {
      xtype: 'nx-repository-settings-hosted-form',
      template: me.template,
      api: {
        submit: 'NX.direct.coreui_legacy_Repository.createHosted'
      },
      settingsFormSuccessMessage: function(data) {
        return NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_CREATE_HOSTED_SUCCESS') + data['id'];
      }
    };

    me.callParent(arguments);
  }

});
