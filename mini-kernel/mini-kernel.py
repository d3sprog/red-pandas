#!/usr/bin/env python3
from flask import Blueprint, Flask, request, jsonify
from flask_cors import CORS
import json

python_service_blueprint = Blueprint("python_service", __name__)

@python_service_blueprint.route("/", methods=["GET"])
def test_request():
    return "Python service is alive!"

# =======================================================================================
# Evaluation
# =======================================================================================

# This also makes pandas as pd available inside the code passed to exec/eval
# (and this is needed because exec-ing import does not work - though I'm sure
# it can be fixed in some way)
import pandas as pd

vars = dict()

@python_service_blueprint.route("/exec", methods=['POST'])
def exec_request():
    exec(request.data.decode("utf-8"), None, vars)
    return ""

@python_service_blueprint.route("/eval", methods=['POST'])
def eval_request():
    res = eval(request.data.decode("utf-8"), None, vars)
    if isinstance(res, pd.DataFrame):
      return res.to_json()
    else:
      return jsonify(res)

@python_service_blueprint.route("/locals", methods=['GET'])
def locals_request():
    res = { k: type(v).__name__ for k,v in vars.items() }
    return jsonify(res)

@python_service_blueprint.route("/getcols/<string:df>", methods=['GET'])
def getcols_request(df):
    # We could use `dtypes` to look at the types, but this then reports the
    # type of strings as `obj` and that is not very nice. So instead of
    # doing that, I look at the types of data in the first row
    # (this is not exactly right....)
    res = vars[df].iloc[0].apply(lambda v:type(v).__name__)
    return res.to_json()

# =======================================================================================
# Parsing - code from Jan Hruby, who in turn got it somewhere else. See:
# https://github.com/Hrubian/Pandalyzer/blob/main/src/main/resources/python_converter.py
# =======================================================================================

import ast
from _ast import AST, Constant
import codecs

BUILTIN_PURE = (int, float, bool)
BUILTIN_BYTES = (bytearray, bytes)
BUILTIN_STR = (str)

def decode_str(value):
    return value

def decode_bytes(value):
    try:
        return value.decode('utf-8')
    except:
        return codecs.getencoder('hex_codec')(value)[0].decode('utf-8')
    
def get_node_type(node):
    if isinstance(node, Constant):
        val = getattr(node, "value")
        if isinstance(val, str):
            return "StringConstant"
        elif isinstance(val, bool):
            return "BoolConstant"
        elif isinstance(val, int):
            return "IntConstant"
        elif isinstance(val, float):
            return "FloatConstant"
        elif val is None:
            return "NoneConstant"
        else:
            raise Exception("Unhandled Constant case type" + str(type(val)))
    else:
        return node.__class__.__name__

def get_value(attr_value):
    if attr_value is None:
        return attr_value
    if isinstance(attr_value, BUILTIN_PURE):
        return attr_value
    if isinstance(attr_value, BUILTIN_BYTES):
        return decode_bytes(attr_value)
    if isinstance(attr_value, BUILTIN_STR):
        return decode_str(attr_value)
    if isinstance(attr_value, complex):
        return str(attr_value)
    if isinstance(attr_value, list):
        return [get_value(x) for x in attr_value]
    if isinstance(attr_value, AST):
        return ast_to_json(attr_value)
    if isinstance(attr_value, type(Ellipsis)):
        return '...'
    else:
        raise Exception("unknown case for '%s' of type '%s'" % (attr_value, type(attr_value)))

def ast_to_json(node):
    json = {}
    json["_type"] = get_node_type(node)
    for attribute in dir(node):
        if attribute.startswith('_'):
            continue
        json[attribute] = get_value(getattr(node, attribute))
    return json

@python_service_blueprint.route("/parse", methods=["GET"])
def parse_get_request():
    code = request.args.get('code', None)
    return ast_to_json(ast.parse(code))

@python_service_blueprint.route("/parse", methods=['POST'])
def parse_post_request():
    code = request.data.decode("utf-8")
    print("PARSE!")
    print(code)
    return ast_to_json(ast.parse(code))

# =======================================================================================
# Run the HTTP server
# =======================================================================================

app = Flask("main")
CORS(app)
app.register_blueprint(python_service_blueprint)
app.run(host='0.0.0.0',port=7101, debug=True, use_reloader=False)