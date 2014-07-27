/*
 * Copyright 2012 LinkedIn Corp.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

$.namespace('azkaban');

var nodeEditView;
azkaban.NodeEditView = Backbone.View.extend({
	events : {
		"click" : "closeEditingTarget",
		"click #set-btn": "handleSet",
		"click #job-cancel-btn": "handleCancel",
		"click #job-close-btn": "handleCancel",
		"click #add-btn": "handleAddRow",
		"click table .editable": "handleEditColumn",
		"click table .remove-btn": "handleRemoveColumn"
	},

	initialize: function(setting) {
		this.projectURL = contextURL + "manager"
		this.generalParams = {}
		this.overrideParams = {}
		this.editNode = {}
	},

	handleCancel: function(evt) {
		$('#node-edit-pane').hide();
		var tbl = document.getElementById("generalProps").tBodies[0];
		var rows = tbl.rows;
		var len = rows.length;
		for (var i = 0; i < len-1; i++) {
			tbl.deleteRow(0);
		}
		evt.preventDefault();
		evt.stopPropagation();
	},

	show: function(node) {
		this.editNode = node;
		$('#node-edit-pane').modal();
		var handleAddRow = this.handleAddRow;
		var editName = $('#jobName');
		var spanName = $(editName[0]).find('span');
		$(spanName[0]).text(node.name);
		var editType = $('#jobType');
		var spanType = $(editType[0]).find('span');
		$(spanType[0]).text(node.type);

		var overrideParams = node.props;
                for (var okey in overrideParams) {
                       if (okey != 'type' && okey != 'dependencies') {
                                var row = handleAddRow();
                                var td = $(row).find('span');
                               	$(td[0]).text(okey);
                                $(td[1]).text(overrideParams[okey]);
                           }
               }
	},

	handleSet: function(evt) {
		this.closeEditingTarget(evt);
		var jobOverride = {};
		var editRows = $(".editRow");
		for (var i = 0; i < editRows.length; ++i) {
			var row = editRows[i];
			var td = $(row).find('span');
			var key = $(td[0]).text();
			var val = $(td[1]).text();

			if (key && key.length > 0) {
				jobOverride[key] = val;
			}
		}
		var editName = $('#jobName');
		var spanName = $(editName[0]).find('span');
		var nodeName = $(spanName[0]).text();
		var editType = $('#jobType');
		var spanType = $(editType[0]).find('span');
		var nodeType = $(spanType[0]).text();
		var node = this.editNode;
		node.props = jobOverride;
		if(nodeName)
			node.name = nodeName;
		if(nodeType)
			node.type = nodeType;
		graph.updateNode(node);
		$('#node-edit-pane').hide();
		var tbl = document.getElementById("generalProps").tBodies[0];
		var rows = tbl.rows;
		var len = rows.length;
		for (var i = 0; i < len-1; i++) {
			tbl.deleteRow(0);
		}
		evt.preventDefault();
		evt.stopPropagation();
	},

	handleAddRow: function(evt) {
		var tr = document.createElement("tr");
		var tdName = document.createElement("td");
		$(tdName).addClass('property-key');
		var tdValue = document.createElement("td");

		var remove = document.createElement("div");
		$(remove).addClass("pull-right").addClass('remove-btn');
		var removeBtn = document.createElement("button");
		$(removeBtn).attr('type', 'button');
		$(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
		$(removeBtn).text('Delete');
		$(remove).append(removeBtn);

		var nameData = document.createElement("span");
		$(nameData).addClass("spanValue");
		var valueData = document.createElement("span");
		$(valueData).addClass("spanValue");

		$(tdName).append(nameData);
		$(tdName).addClass("editable");
		nameData.myparent = tdName;

		$(tdValue).append(valueData);
		$(tdValue).append(remove);
		$(tdValue).addClass("editable");
		$(tdValue).addClass("value");
		valueData.myparent = tdValue;

		$(tr).addClass("editRow");
		$(tr).append(tdName);
		$(tr).append(tdValue);

		$(tr).insertBefore("#addRow");
		return tr;
	},

	handleEditColumn: function(evt) {
		var curTarget = evt.currentTarget;
		if (this.editingTarget != curTarget) {
			this.closeEditingTarget(evt);

			var text = $(curTarget).children(".spanValue").text();
			$(curTarget).empty();

			var input = document.createElement("input");
			$(input).attr("type", "text");
			$(input).addClass("form-control").addClass("input-sm");
			$(input).val(text);

			$(curTarget).addClass("editing");
			$(curTarget).append(input);
			$(input).focus();
			var obj = this;
			$(input).keypress(function(evt) {
				if (evt.which == 13) {
					obj.closeEditingTarget(evt);
				}
			});
			this.editingTarget = curTarget;
		}

		evt.preventDefault();
		evt.stopPropagation();
	},

	handleRemoveColumn: function(evt) {
		var curTarget = evt.currentTarget;
		// Should be the table
		var row = curTarget.parentElement.parentElement;
		$(row).remove();
	},

	closeEditingTarget: function(evt) {
		if (this.editingTarget == null ||
				this.editingTarget == evt.target ||
				this.editingTarget == evt.target.myparent) {
			return;
		}
		var input = $(this.editingTarget).children("input")[0];
		var text = $(input).val();
		$(input).remove();

		var valueData = document.createElement("span");
		$(valueData).addClass("spanValue");
		$(valueData).text(text);

		if ($(this.editingTarget).hasClass("value")) {
			var remove = document.createElement("div");
			$(remove).addClass("pull-right").addClass('remove-btn');
			var removeBtn = document.createElement("button");
			$(removeBtn).attr('type', 'button');
			$(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
			$(removeBtn).text('Delete');
			$(remove).append(removeBtn);
			$(this.editingTarget).append(remove);
		}

		$(this.editingTarget).removeClass("editing");
		$(this.editingTarget).append(valueData);
		valueData.myparent = this.editingTarget;
		this.editingTarget = null;
	}
});

$(function() {
	nodeEditView = new azkaban.NodeEditView({
		el: $('#node-edit-pane')
	});
});
