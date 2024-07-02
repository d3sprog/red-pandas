# Python mini-kernel service

The directory contains a simple Python service (mini-kernel) that can be used in place of a real Jupyter kernel. It provides some basic functionality that we will need in our checker - parsing Python code (returns JSON), running Python code (because we assume users will run code and this may give us more type information) and getting types of runtime values (because we can then be more precise).

## Running the service

Run the following:

```
pip install flask
pip install flask-cors
python mini-kernel.py
```

## Experimenting with the service

[Install JupyterLab](https://jupyterlab.readthedocs.io/en/stable/getting_started/installation.html) and then open it using:


```
jupyter lab
```

Look at the following:

* `samples.ipynb` is a fairly simple but not entirely trivial sample notebook that uses `rooms.csv` as input data. This may be a good starting point for type checking.
* `samples.md` has some comments on how this could work.
* `mini-kernel.jpynb` has some examples of calling the service (to get information for the first cell of the sample notebook) using `curl`