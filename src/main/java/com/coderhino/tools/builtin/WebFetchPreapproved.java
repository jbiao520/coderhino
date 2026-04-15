package com.coderhino.tools.builtin;

// For legal and security concerns, we typically only allow Web Fetch to access
// domains that the user has provided in some form. However, we make an
// exception for a list of preapproved domains that are code-related.
//
// SECURITY WARNING: These preapproved domains are ONLY for WebFetch (GET requests only).
// The sandbox system deliberately does NOT inherit this list for network restrictions,
// as arbitrary network access (POST, uploads, etc.) to these domains could enable
// data exfiltration. Some domains like huggingface.co, kaggle.com, and nuget.org
// allow file uploads and would be dangerous for unrestricted network access.

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ported from src/tools/WebFetchTool/preapproved.ts.
 * Contains all 80+ preapproved hosts for WebFetch tool access.
 * Lookups are O(1) for hostname-only entries via a Set, and use a Map
 * of path-prefix lists for path-scoped entries (e.g. "github.com/anthropics").
 */
public final class WebFetchPreapproved {

    private WebFetchPreapproved() {
        // utility class — not instantiable
    }

    /**
     * The raw set of all preapproved host entries, mirroring PREAPPROVED_HOSTS
     * from preapproved.ts. Entries containing "/" are path-scoped.
     */
    private static final Set<String> ALL_ENTRIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        // Anthropic
        "platform.claude.com",
        "code.claude.com",
        "modelcontextprotocol.io",
        "github.com/anthropics",
        "agentskills.io",

        // Top Programming Languages
        "docs.python.org",           // Python
        "en.cppreference.com",       // C/C++ reference
        "docs.oracle.com",           // Java
        "learn.microsoft.com",       // C#/.NET / Azure
        "developer.mozilla.org",     // JavaScript/Web APIs (MDN)
        "go.dev",                    // Go
        "pkg.go.dev",                // Go docs
        "www.php.net",               // PHP
        "docs.swift.org",            // Swift
        "kotlinlang.org",            // Kotlin
        "ruby-doc.org",              // Ruby
        "doc.rust-lang.org",         // Rust
        "www.typescriptlang.org",    // TypeScript

        // Web & JavaScript Frameworks/Libraries
        "react.dev",                 // React
        "angular.io",                // Angular
        "vuejs.org",                 // Vue.js
        "nextjs.org",                // Next.js
        "expressjs.com",             // Express.js
        "nodejs.org",                // Node.js
        "bun.sh",                    // Bun
        "jquery.com",                // jQuery
        "getbootstrap.com",          // Bootstrap
        "tailwindcss.com",           // Tailwind CSS
        "d3js.org",                  // D3.js
        "threejs.org",               // Three.js
        "redux.js.org",              // Redux
        "webpack.js.org",            // Webpack
        "jestjs.io",                 // Jest
        "reactrouter.com",           // React Router

        // Python Frameworks & Libraries
        "docs.djangoproject.com",    // Django
        "flask.palletsprojects.com", // Flask
        "fastapi.tiangolo.com",      // FastAPI
        "pandas.pydata.org",         // Pandas
        "numpy.org",                 // NumPy
        "www.tensorflow.org",        // TensorFlow
        "pytorch.org",               // PyTorch
        "scikit-learn.org",          // Scikit-learn
        "matplotlib.org",            // Matplotlib
        "requests.readthedocs.io",   // Requests
        "jupyter.org",               // Jupyter

        // PHP Frameworks
        "laravel.com",               // Laravel
        "symfony.com",               // Symfony
        "wordpress.org",             // WordPress

        // Java Frameworks & Libraries
        "docs.spring.io",            // Spring
        "hibernate.org",             // Hibernate
        "tomcat.apache.org",         // Tomcat
        "gradle.org",                // Gradle
        "maven.apache.org",          // Maven

        // .NET & C# Frameworks
        "asp.net",                   // ASP.NET
        "dotnet.microsoft.com",      // .NET
        "nuget.org",                 // NuGet
        "blazor.net",                // Blazor

        // Mobile Development
        "reactnative.dev",           // React Native
        "docs.flutter.dev",          // Flutter
        "developer.apple.com",       // iOS/macOS
        "developer.android.com",     // Android

        // Data Science & Machine Learning
        "keras.io",                  // Keras
        "spark.apache.org",          // Apache Spark
        "huggingface.co",            // Hugging Face
        "www.kaggle.com",            // Kaggle

        // Databases
        "www.mongodb.com",           // MongoDB
        "redis.io",                  // Redis
        "www.postgresql.org",        // PostgreSQL
        "dev.mysql.com",             // MySQL
        "www.sqlite.org",            // SQLite
        "graphql.org",               // GraphQL
        "prisma.io",                 // Prisma

        // Cloud & DevOps
        "docs.aws.amazon.com",       // AWS
        "cloud.google.com",          // Google Cloud
        // "learn.microsoft.com" already listed above (Azure shares the same host)
        "kubernetes.io",             // Kubernetes
        "www.docker.com",            // Docker
        "www.terraform.io",          // Terraform
        "www.ansible.com",           // Ansible
        "vercel.com/docs",           // Vercel
        "docs.netlify.com",          // Netlify
        "devcenter.heroku.com",      // Heroku

        // Testing & Monitoring
        "cypress.io",                // Cypress
        "selenium.dev",              // Selenium

        // Game Development
        "docs.unity.com",            // Unity
        "docs.unrealengine.com",     // Unreal Engine

        // Other Essential Tools
        "git-scm.com",               // Git
        "nginx.org",                 // Nginx
        "httpd.apache.org"           // Apache HTTP Server
    )));

    /**
     * Hostname-only entries — no path restriction. O(1) Set lookup.
     */
    private static final Set<String> HOSTNAME_ONLY;

    /**
     * Path-scoped entries: hostname → list of required path prefixes.
     * For example: "github.com" → ["/anthropics"]
     */
    private static final Map<String, List<String>> PATH_PREFIXES;

    static {
        Set<String> hosts = new HashSet<>();
        Map<String, List<String>> paths = new HashMap<>();

        for (String entry : ALL_ENTRIES) {
            int slash = entry.indexOf('/');
            if (slash == -1) {
                hosts.add(entry);
            } else {
                String host = entry.substring(0, slash);
                String path = entry.substring(slash); // includes leading "/"
                paths.computeIfAbsent(host, k -> new java.util.ArrayList<>()).add(path);
            }
        }

        HOSTNAME_ONLY = Collections.unmodifiableSet(hosts);
        // Make each list unmodifiable
        Map<String, List<String>> immutablePaths = new HashMap<>();
        for (Map.Entry<String, List<String>> e : paths.entrySet()) {
            immutablePaths.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        PATH_PREFIXES = Collections.unmodifiableMap(immutablePaths);
    }

    /**
     * Returns true if the given hostname + pathname combination is preapproved.
     *
     * <p>For hostname-only entries (e.g. "react.dev"), any pathname is accepted.
     * For path-scoped entries (e.g. "github.com/anthropics"), the pathname must
     * equal the prefix exactly OR start with the prefix followed by "/" to enforce
     * path-segment boundaries (so "/anthropics" does NOT match "/anthropics-evil").
     *
     * @param hostname the bare hostname (no scheme, no port, no path)
     * @param pathname the request path (should start with "/")
     * @return true if the URL is preapproved
     */
    public static boolean isPreapprovedHost(String hostname, String pathname) {
        if (HOSTNAME_ONLY.contains(hostname)) {
            return true;
        }
        List<String> prefixes = PATH_PREFIXES.get(hostname);
        if (prefixes != null) {
            for (String p : prefixes) {
                // Enforce path segment boundaries: "/anthropics" must not match
                // "/anthropics-evil/malware". Only exact match or a "/" after the
                // prefix is allowed.
                if (pathname.equals(p) || pathname.startsWith(p + "/")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Convenience method — parses a full URL and delegates to
     * {@link #isPreapprovedHost(String, String)}.
     *
     * @param url the full URL string (e.g. "https://react.dev/docs/hooks")
     * @return true if the URL is preapproved; false on parse error or mismatch
     */
    public static boolean isPreapprovedUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null) {
                return false;
            }
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            return isPreapprovedHost(host, path);
        } catch (Exception e) {
            return false;
        }
    }
}
