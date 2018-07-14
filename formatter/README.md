# Intellij Code Formatter

Dockerized version of Intellij CE's code formatter.

## Building

```
  $ docker build . -t formatter
```

## Usage

Run the container and mount the directory that contains the source files that you want to 
format to `/src` inside the container. It will format all files recursively using the rules defined 
in `format.xml`. For example, to format the files in the current directory use:

```
  $ docker run --rm -it -v $PWD:/src formatter
```

You may also pass in additional [parameters](https://www.jetbrains.com/help/idea/command-line-formatter.html)
that are supported by Intellij. For example, to format only kotlin files in the current directory:

```
  $ docker run --rm -it -v $PWD:/src formatter -m *.kt
```

## Customizing

You can replace `format.xml` with your own formatter configuration to customize
the settings. See the official [documentation](https://www.jetbrains.com/help/idea/settings-code-style.html)
for more details.
