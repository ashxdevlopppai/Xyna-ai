import json
import os
import numpy as np
from datetime import datetime
import torch
from transformers import (
    AutoModelForCausalLM, 
    AutoTokenizer,
    AutoModelForSequenceClassification,
    pipeline
)
import sqlite3
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
import random
import pickle

# Global variables for models
tokenizer = None
model = None
intent_classifier = None
sentiment_analyzer = None
qa_model = None
knowledge_base = None
tfidf_vectorizer = None

class KnowledgeBase:
    def __init__(self, db_path):
        self.conn = sqlite3.connect(db_path)
        self.cursor = self.conn.cursor()
        self.setup_database()
        
    def setup_database(self):
        self.cursor.execute('''
            CREATE TABLE IF NOT EXISTS knowledge (
                id INTEGER PRIMARY KEY,
                question TEXT,
                answer TEXT,
                category TEXT,
                last_used TIMESTAMP
            )
        ''')
        self.conn.commit()
    
    def add_entry(self, question, answer, category):
        self.cursor.execute(
            'INSERT INTO knowledge (question, answer, category, last_used) VALUES (?, ?, ?, ?)',
            (question, answer, category, datetime.now())
        )
        self.conn.commit()
    
    def find_similar(self, query, threshold=0.7):
        self.cursor.execute('SELECT question, answer FROM knowledge')
        entries = self.cursor.fetchall()
        if not entries:
            return None
            
        questions = [entry[0] for entry in entries]
        if tfidf_vectorizer:
            query_vec = tfidf_vectorizer.transform([query])
            question_vecs = tfidf_vectorizer.transform(questions)
            similarities = cosine_similarity(query_vec, question_vecs)[0]
            
            best_match_idx = np.argmax(similarities)
            if similarities[best_match_idx] >= threshold:
                return entries[best_match_idx][1]
        return None

def load_models(cache_dir):
    """Load all necessary offline models."""
    global tokenizer, model, intent_classifier, sentiment_analyzer, qa_model, knowledge_base, tfidf_vectorizer
    
    try:
        # Load small offline language model (Phi-1.5 or similar)
        model_path = os.path.join(cache_dir, "tiny_llm")
        if os.path.exists(model_path):
            tokenizer = AutoTokenizer.from_pretrained(model_path)
            model = AutoModelForCausalLM.from_pretrained(model_path)
        
        # Load specialized models
        qa_model = pipeline(
            "question-answering",
            model=os.path.join(cache_dir, "qa_model"),
            tokenizer=os.path.join(cache_dir, "qa_tokenizer")
        ) if os.path.exists(os.path.join(cache_dir, "qa_model")) else None
        
        intent_classifier = pipeline(
            "text-classification",
            model=os.path.join(cache_dir, "intent_model"),
            tokenizer=os.path.join(cache_dir, "intent_tokenizer")
        ) if os.path.exists(os.path.join(cache_dir, "intent_model")) else None
        
        sentiment_analyzer = pipeline(
            "sentiment-analysis",
            model=os.path.join(cache_dir, "sentiment_model"),
            tokenizer=os.path.join(cache_dir, "sentiment_tokenizer")
        ) if os.path.exists(os.path.join(cache_dir, "sentiment_model")) else None
        
        # Initialize knowledge base
        knowledge_base = KnowledgeBase(os.path.join(cache_dir, "knowledge.db"))
        
        # Load TF-IDF vectorizer
        vectorizer_path = os.path.join(cache_dir, "tfidf_vectorizer.pkl")
        if os.path.exists(vectorizer_path):
            with open(vectorizer_path, 'rb') as f:
                tfidf_vectorizer = pickle.load(f)
            
    except Exception as e:
        print(f"Error loading models: {e}")

def process_input(input_text, chat_history="", image_path=None):
    """Process input using offline models."""
    try:
        # Check knowledge base first
        if knowledge_base:
            cached_response = knowledge_base.find_similar(input_text)
            if cached_response:
                return cached_response
        
        # Analyze input
        intent = analyze_intent(input_text)
        sentiment = analyze_sentiment(input_text)
        
        # Handle different types of inputs
        if intent == "question" and qa_model:
            # Use QA model for questions
            context = chat_history[-1000:] if chat_history else ""
            answer = qa_model(question=input_text, context=context)
            response = answer['answer']
        elif model and tokenizer:
            # Generate response using language model
            context = create_context(chat_history, input_text, intent, sentiment)
            response = generate_response(context)
        else:
            # Use fallback response
            response = get_enhanced_fallback_response(intent, sentiment, input_text)
        
        # Cache successful responses
        if response and knowledge_base:
            knowledge_base.add_entry(input_text, response, intent)
        
        return response
        
    except Exception as e:
        return f"I'm currently in offline mode and encountered an error: {str(e)}"

def create_context(chat_history, input_text, intent, sentiment):
    """Create rich context for response generation."""
    return f"""Chat History: {chat_history}
User Intent: {intent}
User Sentiment: {sentiment}
Current Time: {datetime.now().strftime('%Y-%m-%d %H:%M')}
User: {input_text}
Assistant:"""

def generate_response(context):
    """Generate response using the language model."""
    inputs = tokenizer(context, return_tensors="pt", max_length=512, truncation=True)
    outputs = model.generate(
        inputs["input_ids"],
        max_length=200,
        num_return_sequences=1,
        temperature=0.7,
        top_p=0.9,
        do_sample=True,
        pad_token_id=tokenizer.eos_token_id
    )
    response = tokenizer.decode(outputs[0], skip_special_tokens=True)
    return response.split("Assistant:")[-1].strip()

def get_enhanced_fallback_response(intent, sentiment, input_text):
    """Get enhanced fallback response with more context awareness."""
    responses = {
        "greeting": [
            "Hello! I'm in offline mode but I'm here to help.",
            "Hi there! I'm running offline but I'll do my best to assist you.",
            "Namaste! I'm operating offline but ready to help!"
        ],
        "question": [
            "I understand you have a question about '{}'. While offline, I can provide basic assistance.",
            "I'll try to help with your question using my offline knowledge about '{}'.",
            "Let me attempt to answer that with my offline capabilities regarding '{}'."
        ],
        "urgent": [
            "I notice this is urgent regarding '{}'. While offline, I'll do my best to help.",
            "This seems important about '{}'. I'm in offline mode but I'll try to assist.",
            "I understand the urgency about '{}'. Though offline, I'll help however I can."
        ],
        "statement": [
            "I understand your point about '{}'. While offline, I can still engage in conversation.",
            "I'm listening to your thoughts on '{}', though I'm in offline mode.",
            "I hear what you're saying about '{}'. Even offline, I'm here to chat."
        ]
    }
    
    # Extract key topic from input
    topic = extract_topic(input_text)
    
    # Get base response template
    base_responses = responses.get(intent, responses["statement"])
    selected_response = random.choice(base_responses)
    
    # Format with topic if applicable
    try:
        return selected_response.format(topic)
    except:
        return selected_response

def extract_topic(text):
    """Extract main topic from text."""
    # Simple topic extraction using noun phrases
    words = text.lower().split()
    stop_words = {"the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for"}
    topic_words = [word for word in words if word not in stop_words]
    return " ".join(topic_words[:3]) if topic_words else "this topic"

def analyze_intent(text):
    """Analyze intent using offline model."""
    if intent_classifier:
        # Use loaded intent classifier
        return intent_classifier(text)
    else:
        # Fallback intent analysis using rules
        text = text.lower()
        if any(word in text for word in ["help", "emergency", "urgent"]):
            return "urgent"
        elif any(word in text for word in ["how", "what", "why", "when"]):
            return "question"
        elif any(word in text for word in ["hello", "hi", "hey"]):
            return "greeting"
        else:
            return "statement"

def analyze_sentiment(text):
    """Analyze sentiment using offline model."""
    if sentiment_analyzer:
        # Use loaded sentiment analyzer
        return sentiment_analyzer(text)
    else:
        # Fallback sentiment analysis using rules
        positive_words = {"good", "great", "awesome", "happy", "love", "thanks"}
        negative_words = {"bad", "hate", "angry", "sad", "terrible", "worst"}
        
        words = set(text.lower().split())
        pos_count = len(words.intersection(positive_words))
        neg_count = len(words.intersection(negative_words))
        
        if pos_count > neg_count:
            return "positive"
        elif neg_count > pos_count:
            return "negative"
        else:
            return "neutral"

def update_models(cache_dir):
    """Update offline models from online sources."""
    try:
        # Download and save tiny language model
        model = AutoModelForCausalLM.from_pretrained("microsoft/phi-1_5")
        tokenizer = AutoTokenizer.from_pretrained("microsoft/phi-1_5")
        
        model_path = os.path.join(cache_dir, "tiny_llm")
        model.save_pretrained(model_path)
        tokenizer.save_pretrained(model_path)
        
        # Update other models
        update_intent_classifier(cache_dir)
        update_sentiment_analyzer(cache_dir)
        
    except Exception as e:
        print(f"Error updating models: {e}")

def update_intent_classifier(cache_dir):
    """Update intent classifier model."""
    # Implementation for updating intent classifier
    pass

def update_sentiment_analyzer(cache_dir):
    """Update sentiment analyzer model."""
    # Implementation for updating sentiment analyzer
    pass 