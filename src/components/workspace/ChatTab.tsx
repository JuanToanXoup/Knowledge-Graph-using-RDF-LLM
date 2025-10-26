import { useState } from 'react';
import { Send, Copy, ThumbsUp, ThumbsDown, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Card } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  facts?: string[];
}

interface ChatTabProps {
  graphId: string;
}

export const ChatTab = ({ graphId }: ChatTabProps) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const { toast } = useToast();

  const handleSend = async () => {
    if (!input.trim()) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: input,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setLoading(true);

    try {
      const response = await fetch('http://localhost:8000/question_answer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ graph_id: graphId, question: input }),
      });
      const data = await response.json();

      const assistantMessage: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: data.answer,
        facts: data.relevant_facts?.map((f: any) => f.text) || [],
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (error) {
      toast({
        title: 'Error',
        description: 'Failed to get answer. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="h-[calc(100vh-10rem)] sm:h-[calc(100vh-12rem)] flex flex-col lg:flex-row gap-4 sm:gap-6">
      {/* Chat History Sidebar - Hidden on mobile */}
      <div className="hidden lg:flex lg:w-80 flex-col border-r border-border">
        <div className="p-4 border-b border-border">
          <Button className="w-full gap-2">
            <Plus className="w-4 h-4" />
            New Chat
          </Button>
        </div>
        <div className="flex-1 overflow-y-auto p-4">
          <p className="text-sm text-muted-foreground text-center py-8">
            No previous conversations
          </p>
        </div>
      </div>

      {/* Main Chat */}
      <div className="flex-1 flex flex-col min-h-0">
        <div className="mb-3 sm:mb-4">
          <h2 className="text-2xl sm:text-3xl font-bold">Chat & Q&A</h2>
          <p className="text-sm sm:text-base text-muted-foreground">
            Ask questions about your knowledge graph
          </p>
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto space-y-3 sm:space-y-4 mb-3 sm:mb-4">
          {messages.length === 0 ? (
            <div className="h-full flex items-center justify-center">
              <div className="text-center space-y-2">
                <p className="text-muted-foreground">
                  Start a conversation by asking a question
                </p>
                <p className="text-sm text-muted-foreground">
                  Try: "What are the main entities in this document?"
                </p>
              </div>
            </div>
          ) : (
            messages.map((message) => (
              <Card
                key={message.id}
                className={`p-4 sm:p-6 max-w-full sm:max-w-4xl ${
                  message.role === 'user'
                    ? 'ml-auto bg-muted'
                    : 'mr-auto border-l-2 border-primary'
                }`}
              >
                <p className="text-sm sm:text-base leading-relaxed whitespace-pre-wrap break-words">
                  {message.content}
                </p>

                {message.facts && message.facts.length > 0 && (
                  <details className="mt-4">
                    <summary className="cursor-pointer text-sm text-primary font-medium">
                      Relevant Facts ({message.facts.length})
                    </summary>
                    <ul className="mt-2 space-y-2 pl-4">
                      {message.facts.map((fact, i) => (
                        <li key={i} className="text-sm text-muted-foreground">
                          • {fact}
                        </li>
                      ))}
                    </ul>
                  </details>
                )}

                {message.role === 'assistant' && (
                  <div className="flex items-center gap-2 mt-4 pt-4 border-t border-border">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        navigator.clipboard.writeText(message.content);
                        toast({ title: 'Copied to clipboard' });
                      }}
                    >
                      <Copy className="w-4 h-4" />
                    </Button>
                    <Button variant="ghost" size="sm">
                      <ThumbsUp className="w-4 h-4" />
                    </Button>
                    <Button variant="ghost" size="sm">
                      <ThumbsDown className="w-4 h-4" />
                    </Button>
                  </div>
                )}
              </Card>
            ))
          )}

          {loading && (
            <Card className="p-6 max-w-4xl border-l-2 border-primary">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-primary animate-pulse" />
                <div className="w-2 h-2 rounded-full bg-primary animate-pulse delay-75" />
                <div className="w-2 h-2 rounded-full bg-primary animate-pulse delay-150" />
              </div>
            </Card>
          )}
        </div>

        {/* Input */}
        <Card className="p-3 sm:p-4 border-border">
          <div className="flex gap-2 sm:gap-4">
            <Textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask anything about your document..."
              className="min-h-[60px] text-sm sm:text-base border-border focus:border-primary resize-none"
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
            />
            <Button
              onClick={handleSend}
              disabled={!input.trim() || loading}
              className="h-full px-4 sm:px-6 shadow-glow-primary hover:shadow-glow-hover"
            >
              <Send className="w-4 h-4 sm:w-5 sm:h-5" />
            </Button>
          </div>
          <p className="text-xs text-muted-foreground mt-2">
            Press Enter to send, Shift+Enter for new line
          </p>
        </Card>
      </div>
    </div>
  );
};
