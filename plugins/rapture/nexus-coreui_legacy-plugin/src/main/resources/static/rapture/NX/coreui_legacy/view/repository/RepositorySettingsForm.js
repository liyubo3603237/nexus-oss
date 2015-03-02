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
 * Repository "Settings" form.
 *
 * @since 3.0
 */
Ext.define('NX.coreui_legacy.view.repository.RepositorySettingsForm', {
  extend: 'NX.view.SettingsForm',
  alias: 'widget.nx-coreui_legacy-repository-settings-form',
  requires: [
    'NX.Conditions',
    'NX.I18n'
  ],

  /**
   * @cfg template repository template object
   */

  editableMarker: NX.I18n.get('LEGACY_ADMIN_REPOSITORIES_UPDATE_ERROR'),

  initComponent: function() {
    var me = this;

    me.editableCondition = me.editableCondition || NX.Conditions.isPermitted('nexus:repositories', 'update');

    me.items = me.items || [];
    Ext.Array.insert(me.items, 0, [
      {
        xtype: 'nx-coreui_legacy-repository-settings-common'
      }
    ]);

    me.callParent(arguments);

    me.down('#providerName').setValue(me.template.providerName);
    me.down('#formatName').setValue(me.template.formatName);
  }

});
