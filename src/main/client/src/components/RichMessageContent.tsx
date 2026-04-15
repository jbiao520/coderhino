import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';

interface RichMessageContentProps {
  text: string;
}

export default function RichMessageContent({ text }: RichMessageContentProps) {
  return (
    <div style={styles.root} data-testid="rich-message-content">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeRaw, rehypeSanitize]}
        components={{
          p: ({ children }) => <p style={styles.paragraph}>{children}</p>,
          h1: ({ children }) => <h1 style={styles.headingLg}>{children}</h1>,
          h2: ({ children }) => <h2 style={styles.headingMd}>{children}</h2>,
          h3: ({ children }) => <h3 style={styles.headingSm}>{children}</h3>,
          ul: ({ children }) => <ul style={styles.unorderedList}>{children}</ul>,
          ol: ({ children }) => <ol style={styles.orderedList}>{children}</ol>,
          li: ({ children }) => <li style={styles.listItem}>{children}</li>,
          blockquote: ({ children }) => <blockquote style={styles.blockquote}>{children}</blockquote>,
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noreferrer" style={styles.link}>
              {children}
            </a>
          ),
          pre: ({ children }) => <pre style={styles.codeBlock}>{children}</pre>,
          code: ({ children, className }) => (
            isInlineCode(children, className) ? (
              <code style={styles.inlineCode}>{children}</code>
            ) : (
              <code className={className} style={styles.codeText}>{children}</code>
            )
          ),
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
}

const styles = {
  root: {
    fontFamily: 'var(--font-sans)',
    fontSize: 14,
    lineHeight: 1.6,
    color: 'var(--text)',
    wordBreak: 'break-word' as const,
  } as React.CSSProperties,
  paragraph: {
    margin: 0,
  } as React.CSSProperties,
  headingLg: {
    margin: 0,
    fontSize: 20,
    lineHeight: 1.35,
  } as React.CSSProperties,
  headingMd: {
    margin: 0,
    fontSize: 17,
    lineHeight: 1.4,
  } as React.CSSProperties,
  headingSm: {
    margin: 0,
    fontSize: 15,
    lineHeight: 1.45,
  } as React.CSSProperties,
  unorderedList: {
    margin: 0,
    paddingLeft: 20,
  } as React.CSSProperties,
  orderedList: {
    margin: 0,
    paddingLeft: 20,
  } as React.CSSProperties,
  listItem: {
    margin: '2px 0',
  } as React.CSSProperties,
  blockquote: {
    margin: 0,
    paddingLeft: 12,
    borderLeft: '3px solid var(--border)',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  link: {
    color: 'var(--accent)',
  } as React.CSSProperties,
  inlineCode: {
    padding: '1px 6px',
    borderRadius: 'var(--radius-sm)',
    background: 'var(--surface)',
    fontFamily: 'var(--font-mono)',
    fontSize: 13,
  } as React.CSSProperties,
  codeBlock: {
    margin: 0,
    padding: '12px 16px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--surface)',
    overflow: 'auto' as const,
    whiteSpace: 'pre-wrap' as const,
  } as React.CSSProperties,
  codeText: {
    fontFamily: 'var(--font-mono)',
    fontSize: 13,
    lineHeight: 1.5,
  } as React.CSSProperties,
};

function isInlineCode(children: React.ReactNode, className: string | undefined): boolean {
  if (className && className.length > 0) {
    return false;
  }
  const text = React.Children.toArray(children).join('');
  return !text.includes('\n');
}
