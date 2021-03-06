define [
  'backbone.marionette'
  'templates/issues'
], (
  Marionette
  Templates
) ->

  $ = jQuery


  class extends Marionette.ItemView
    className: 'issues-facet-box'
    template: Templates['issues-base-facet']


    modelEvents: ->
      'change': 'render'


    events: ->
      'click .js-issues-facet-toggle': 'toggle'
      'click .js-issues-facet': 'toggleFacet'


    onRender: ->
      @$el.toggleClass 'issues-facet-box-collapsed', !@model.get('enabled')

      property = @model.get 'property'
      value = @options.app.state.get('query')[property]
      if typeof value == 'string'
        value.split(',').forEach (s) =>
          facet = @$('.js-issues-facet').filter("[data-value='#{s}']")
          if facet.length > 0
            parent = facet.parent()
            facet.addClass('active')#.detach().prependTo parent


    toggle: ->
      @options.app.controller.toggleFacet @model.id


    getValue: ->
      @$('.js-issues-facet.active').map(-> $(@).data 'value').get().join()


    toggleFacet: (e) ->
      $(e.currentTarget).toggleClass 'active'
      property = @model.get 'property'
      value = @getValue()
      obj = {}
      obj[property] = value
      @options.app.state.updateFilter obj


    disable: ->
      property = @model.get 'property'
      obj = {}
      obj[property] = null
      @options.app.state.updateFilter obj


    sortValues: (values) ->
      _.sortBy values, (v) -> -v.count


    serializeData: ->
      _.extend super,
        values: @sortValues @model.getValues()
