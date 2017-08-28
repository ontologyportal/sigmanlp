var bratLocation = 'brat';
head.js(
// External libraries
bratLocation + '/client/lib/jquery.min.js', bratLocation
		+ '/client/lib/jquery.svg.min.js', bratLocation
		+ '/client/lib/jquery.svgdom.min.js',

// brat helper modules
bratLocation + '/client/src/configuration.js', bratLocation
		+ '/client/src/util.js',
		bratLocation + '/client/src/annotation_log.js', bratLocation
				+ '/client/lib/webfont.js',

		// brat modules
		bratLocation + '/client/src/dispatcher.js', bratLocation
				+ '/client/src/url_monitor.js', bratLocation
				+ '/client/src/visualizer.js');
var webFontURLs = [ bratLocation + '/static/fonts/Astloch-Bold.ttf',
		bratLocation + '/static/fonts/PT_Sans-Caption-Web-Regular.ttf',
		bratLocation + '/static/fonts/Liberation_Sans-Regular.ttf' ];

// Colorscheme Shamelessly copied from Stanford NLP demo
// http://nlp.stanford.edu:8080/brat/ajax.cgi?action=getConfiguration&protocol=1&name=Stanford-CoreNLP
var colorScheme = {
	"visual_options" : {
		"arc_bundle" : "none"
	},
	"protocol" : 1,
	"unconfigured_types" : [ {
		"borderColor" : "darken",
		"name" : ";",
		"labels" : [ ";" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__COLON__"
	}, {
		"borderColor" : "darken",
		"name" : "Number",
		"labels" : [ "Number", "Num" ],
		"unused" : true,
		"bgColor" : "#df99ff",
		"type" : "NUMBER"
	}, {
		"borderColor" : "darken",
		"name" : "Date",
		"labels" : [ "Date" ],
		"unused" : true,
		"bgColor" : "#9affe6",
		"type" : "DATE"
	}, {
		"borderColor" : "darken",
		"name" : "Duration",
		"labels" : [ "Duration", "Dur" ],
		"unused" : true,
		"bgColor" : "#9affe6",
		"type" : "DURATION"
	}, {
		"borderColor" : "darken",
		"name" : "Set",
		"labels" : [ "Set" ],
		"unused" : true,
		"bgColor" : "#ff7c95",
		"type" : "SET"
	}, {
		"borderColor" : "darken",
		"name" : "\"",
		"labels" : [ "\"" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__DOUBLEQUOTE__"
	}, {
		"borderColor" : "darken",
		"name" : ",",
		"labels" : [ "," ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__COMMA__"
	}, {
		"borderColor" : "darken",
		"name" : "Location",
		"labels" : [ "Location", "Loc" ],
		"unused" : true,
		"bgColor" : "#95dfff",
		"type" : "LOCATION"
	}, {
		"borderColor" : "darken",
		"name" : "(",
		"labels" : [ "(" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "-LRB-"
	}, {
		"borderColor" : "darken",
		"name" : "Misc",
		"labels" : [ "Misc" ],
		"unused" : true,
		"bgColor" : "#f1f447",
		"type" : "MISC"
	}, {
		"borderColor" : "darken",
		"name" : "WP$",
		"labels" : [ "WP$" ],
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "WP__DOLLAR__"
	}, {
		"borderColor" : "darken",
		"name" : "Percent",
		"labels" : [ "Percent", "Perc" ],
		"unused" : true,
		"bgColor" : "#ffa22b",
		"type" : "PERCENT"
	}, {
		"borderColor" : "darken",
		"name" : ".",
		"labels" : [ "." ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__DOT__"
	}, {
		"borderColor" : "darken",
		"name" : "Organization",
		"labels" : [ "Organization", "Org" ],
		"unused" : true,
		"bgColor" : "#8fb2ff",
		"type" : "ORGANIZATION"
	}, {
		"borderColor" : "darken",
		"name" : "PRP$",
		"labels" : [ "PRP$" ],
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "PRP__DOLLAR__"
	}, {
		"borderColor" : "darken",
		"name" : ")",
		"labels" : [ ")" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "-RRB-"
	}, {
		"borderColor" : "darken",
		"name" : "``",
		"labels" : [ "``" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__BACKTICK____BACKTICK__"
	}, {
		"borderColor" : "darken",
		"name" : "Person",
		"labels" : [ "Person", "Pers" ],
		"unused" : true,
		"bgColor" : "#ffccaa",
		"type" : "PERSON"
	}, {
		"borderColor" : "darken",
		"name" : "$",
		"labels" : [ "$" ],
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "__DOLLAR__"
	}, {
		"borderColor" : "darken",
		"name" : "Time",
		"labels" : [ "Time" ],
		"unused" : true,
		"bgColor" : "#9affe6",
		"type" : "TIME"
	}, {
		"borderColor" : "darken",
		"name" : "''",
		"labels" : [ "''" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__SINGLEQUOTE____SINGLEQUOTE__"
	}, {
		"borderColor" : "darken",
		"name" : ";",
		"labels" : [ ";" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__COLON__"
	}, {
		"borderColor" : "darken",
		"name" : "Location",
		"labels" : [ "Location", "Loc" ],
		"unused" : true,
		"bgColor" : "#95dfff",
		"type" : "LOCATION"
	}, {
		"borderColor" : "darken",
		"name" : "VBG",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#adf6a2",
		"type" : "VBG"
	}, {
		"borderColor" : "darken",
		"name" : "VBD",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#adf6a2",
		"type" : "VBD"
	}, {
		"borderColor" : "darken",
		"name" : "Percent",
		"labels" : [ "Percent", "Perc" ],
		"unused" : true,
		"bgColor" : "#ffa22b",
		"type" : "PERCENT"
	}, {
		"borderColor" : "darken",
		"name" : "VBN",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#adf6a2",
		"type" : "VBN"
	}, {
		"borderColor" : "darken",
		"name" : "Duration",
		"labels" : [ "Duration", "Dur" ],
		"unused" : true,
		"bgColor" : "#9affe6",
		"type" : "DURATION"
	}, {
		"borderColor" : "darken",
		"name" : "VBP",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#adf6a2",
		"type" : "VBP"
	}, {
		"borderColor" : "darken",
		"name" : "WDT",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "WDT"
	}, {
		"borderColor" : "darken",
		"name" : "JJ",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#fffda8",
		"type" : "JJ"
	}, {
		"borderColor" : "darken",
		"name" : "WP",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "WP"
	}, {
		"borderColor" : "darken",
		"name" : "Date",
		"labels" : [ "Date" ],
		"unused" : true,
		"bgColor" : "#9affe6",
		"type" : "DATE"
	}, {
		"borderColor" : "darken",
		"name" : "VBZ",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#adf6a2",
		"type" : "VBZ"
	}, {
		"borderColor" : "darken",
		"name" : "DT",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ccadf6",
		"type" : "DT"
	}, {
		"borderColor" : "darken",
		"name" : "Set",
		"labels" : [ "Set" ],
		"unused" : true,
		"bgColor" : "#ff7c95",
		"type" : "SET"
	}, {
		"borderColor" : "darken",
		"name" : "RP",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "RP"
	}, {
		"borderColor" : "darken",
		"name" : "\"",
		"labels" : [ "\"" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__DOUBLEQUOTE__"
	}, {
		"borderColor" : "darken",
		"name" : "FW",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "FW"
	}, {
		"borderColor" : "darken",
		"name" : "POS",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "POS"
	}, {
		"borderColor" : "darken",
		"name" : ",",
		"labels" : [ "," ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__COMMA__"
	}, {
		"borderColor" : "darken",
		"name" : "TO",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ffe8be",
		"type" : "TO"
	}, {
		"borderColor" : "darken",
		"name" : "PRP",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "PRP"
	}, {
		"borderColor" : "darken",
		"name" : "RB",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#fffda8",
		"type" : "RB"
	}, {
		"borderColor" : "darken",
		"name" : "(",
		"labels" : [ "(" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "-LRB-"
	}, {
		"borderColor" : "darken",
		"name" : "NNS",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#a4bced",
		"type" : "NNS"
	}, {
		"borderColor" : "darken",
		"name" : "Organization",
		"labels" : [ "Organization", "Org" ],
		"unused" : true,
		"bgColor" : "#8fb2ff",
		"type" : "ORGANIZATION"
	}, {
		"borderColor" : "darken",
		"name" : "NNP",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#a4bced",
		"type" : "NNP"
	}, {
		"unused" : true,
		"borderColor" : "darken",
		"labels" : null,
		"type" : "SPAN_DEFAULT",
		"name" : "SPAN_DEFAULT"
	}, {
		"borderColor" : "darken",
		"name" : "VB",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#adf6a2",
		"type" : "VB"
	}, {
		"borderColor" : "darken",
		"name" : "WP$",
		"labels" : [ "WP$" ],
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "WP__DOLLAR__"
	}, {
		"borderColor" : "darken",
		"name" : "WRB",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#fffda8",
		"type" : "WRB"
	}, {
		"borderColor" : "darken",
		"name" : "CC",
		"labels" : null,
		"unused" : true,
		"bgColor" : "white",
		"type" : "CC"
	}, {
		"borderColor" : "darken",
		"name" : "LS",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "LS"
	}, {
		"borderColor" : "darken",
		"name" : "PDT",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "PDT"
	}, {
		"borderColor" : "darken",
		"name" : "RBS",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#fffda8",
		"type" : "RBS"
	}, {
		"borderColor" : "darken",
		"name" : "RBR",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#fffda8",
		"type" : "RBR"
	}, {
		"borderColor" : "darken",
		"name" : ".",
		"labels" : [ "." ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__DOT__"
	}, {
		"borderColor" : "darken",
		"name" : "CD",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "CD"
	}, {
		"borderColor" : "darken",
		"name" : "EX",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "EX"
	}, {
		"borderColor" : "darken",
		"name" : "IN",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#ffe8be",
		"type" : "IN"
	}, {
		"borderColor" : "darken",
		"name" : "PRP$",
		"labels" : [ "PRP$" ],
		"unused" : true,
		"bgColor" : "#ccdaf6",
		"type" : "PRP__DOLLAR__"
	}, {
		"borderColor" : "darken",
		"name" : "MD",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#adf6a2",
		"type" : "MD"
	}, {
		"borderColor" : "darken",
		"name" : "NNPS",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#a4bced",
		"type" : "NNPS"
	}, {
		"borderColor" : "darken",
		"name" : "Misc",
		"labels" : [ "Misc" ],
		"unused" : true,
		"bgColor" : "#f1f447",
		"type" : "MISC"
	}, {
		"borderColor" : "darken",
		"name" : "NN",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#a4bced",
		"type" : "NN"
	}, {
		"borderColor" : "darken",
		"name" : ")",
		"labels" : [ ")" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "-RRB-"
	}, {
		"borderColor" : "darken",
		"name" : "JJS",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#fffda8",
		"type" : "JJS"
	}, {
		"borderColor" : "darken",
		"name" : "JJR",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#fffda8",
		"type" : "JJR"
	}, {
		"borderColor" : "darken",
		"name" : "SYM",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "SYM"
	}, {
		"borderColor" : "darken",
		"name" : "``",
		"labels" : [ "``" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__BACKTICK____BACKTICK__"
	}, {
		"borderColor" : "darken",
		"name" : "Person",
		"labels" : [ "Person", "Pers" ],
		"unused" : true,
		"bgColor" : "#ffccaa",
		"type" : "PERSON"
	}, {
		"borderColor" : "darken",
		"name" : "Number",
		"labels" : [ "Number", "Num" ],
		"unused" : true,
		"bgColor" : "#df99ff",
		"type" : "NUMBER"
	}, {
		"borderColor" : "darken",
		"name" : "$",
		"labels" : [ "$" ],
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "__DOLLAR__"
	}, {
		"borderColor" : "darken",
		"name" : "UH",
		"labels" : null,
		"unused" : true,
		"bgColor" : "#e4cbf6",
		"type" : "UH"
	}, {
		"borderColor" : "darken",
		"name" : "Time",
		"labels" : [ "Time" ],
		"unused" : true,
		"bgColor" : "#9affe6",
		"type" : "TIME"
	}, {
		"borderColor" : "darken",
		"name" : "''",
		"labels" : [ "''" ],
		"unused" : true,
		"bgColor" : "#e3e3e3",
		"type" : "__SINGLEQUOTE____SINGLEQUOTE__"
	} ],
	"messages" : [],
	"event_attribute_types" : [],
	"ui_names" : {
		"entities" : "entities",
		"events" : "events",
		"relations" : "relations",
		"attributes" : "attributes"
	},
	"entity_attribute_types" : [],
	"event_types" : [],
	"action" : "getConfiguration",
	"relation_types" : [ {
		"args" : [ {
			"role" : "Arg1",
			"targets" : [ "Mention" ]
		}, {
			"role" : "Arg2",
			"targets" : [ "Mention" ]
		} ],
		"arrowHead" : "none",
		"name" : "Coref",
		"labels" : [ "Coref" ],
		"children" : [],
		"unused" : false,
		"dashArray" : "3,3",
		"attributes" : [],
		"type" : "Coreference",
		"properties" : {
			"symmetric" : true,
			"transitive" : true
		}
	} ],
	"entity_types" : [ {
		"borderColor" : "darken",
		"normalizations" : [],
		"name" : "Mention",
		"arcs" : [ {
			"arrowHead" : "none",
			"dashArray" : "3,3",
			"labels" : [ "Coref" ],
			"type" : "Coreference",
			"targets" : [ "Mention" ]
		} ],
		"labels" : [ "Mention", "Ment", "M" ],
		"unused" : false,
		"bgColor" : "#ffe000",
		"attributes" : [],
		"type" : "Mention",
		"children" : []
	} ],
	"relation_attribute_types" : []
};
head.ready(function() {
	Util.embed(
	// id of the div element where brat should embed the visualisations
	'bratVizDiv',
	// object containing Color scheme data
	colorScheme,
	// object containing annotation data
	docData,
	// Array containing locations of the visualisation fonts
	webFontURLs);
});