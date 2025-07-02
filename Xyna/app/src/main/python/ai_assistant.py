import json
import os
import base64
import requests
import numpy as np
from datetime import datetime, timedelta
from PIL import Image
from io import BytesIO
from com.javris.assistant import BuildConfig

OPENROUTER_API_KEY = BuildConfig.OPENROUTER_API_KEY
OPENROUTER_MODEL = BuildConfig.OPENROUTER_MODEL
OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

def get_system_prompt():
    return """You are Javris, an advanced AI assistant with complete control over the device. You can understand and execute any command, combining multiple actions intelligently.

Core Capabilities:
1. Full Device Control & Automation
2. Natural Language Understanding
3. Contextual Awareness
4. Multi-step Task Execution
5. Proactive Assistance
6. Learning from User Behavior

You should:
1. Think through complex requests step by step
2. Break down multi-part commands into logical sequences
3. Consider context and user preferences
4. Be proactive in suggesting related actions
5. Explain your reasoning when helpful
6. Confirm important actions before executing
7. Learn from user interactions

Example Interactions:
User: "I'm going to a meeting"
Response: Let me help prepare:
1. Checking your calendar for meeting details
2. Setting phone to vibrate
3. Creating quick notes template
4. Ensuring location is saved
5. Setting reminders if needed

User: "I'm tired"
Response: I'll help you wind down:
1. Dimming screen brightness
2. Enabling blue light filter
3. Setting do not disturb
4. Starting relaxing music
5. Setting gentle wake-up alarm

Always maintain context and remember previous interactions to provide more personalized assistance."""

def analyze_context(chat_history):
    """Analyze chat history for context and patterns."""
    try:
        history = json.loads(chat_history) if chat_history else []
        context = {
            'time_of_day': datetime.now().hour,
            'recent_topics': [],
            'user_preferences': {},
            'ongoing_tasks': [],
            'location_context': None,
            'device_state': {}
        }
        
        # Extract recent topics and patterns
        for msg in history[-10:]:
            if msg["type"] == "USER":
                # Extract topics using basic NLP
                words = msg["content"].lower().split()
                context['recent_topics'].extend([w for w in words if len(w) > 3])
                
                # Detect ongoing tasks
                if any(task in msg["content"].lower() for task in ['remind', 'schedule', 'meeting', 'task']):
                    context['ongoing_tasks'].append(msg["content"])
                
                # Detect location context
                if any(loc in msg["content"].lower() for loc in ['at', 'in', 'near', 'location']):
                    context['location_context'] = msg["content"]
        
        return context
    except Exception as e:
        return {'error': str(e)}

def format_messages(user_input, chat_history="", image_path=None):
    messages = [{"role": "system", "content": get_system_prompt()}]
    
    # Add context analysis
    context = analyze_context(chat_history)
    if context and not context.get('error'):
        messages.append({
            "role": "system",
            "content": f"Current context: {json.dumps(context, indent=2)}"
        })
    
    # Add chat history
    if chat_history:
        history = json.loads(chat_history)
        for msg in history:
            role = "user" if msg["type"] == "USER" else "assistant"
            messages.append({
                "role": role,
                "content": msg["content"]
            })
    
    # Handle image input
    if image_path:
        image_content = encode_image(image_path)
        messages.append({
            "role": "user",
            "content": [
                {"type": "text", "text": user_input},
                {"type": "image_url", "image_url": f"data:image/jpeg;base64,{image_content}"}
            ]
        })
    else:
        messages.append({"role": "user", "content": user_input})
    
    return messages

def analyze_task(task):
    """Break down complex tasks into actionable steps with reasoning."""
    try:
        # Task components
        components = {
            'actions': [],
            'targets': [],
            'conditions': [],
            'sequence': [],
            'reasoning': []
        }
        
        # Identify key parts of the task
        task_parts = task.lower().split()
        
        # Extract actions (verbs)
        action_words = ['open', 'search', 'send', 'play', 'find', 'go', 'check', 'set', 'turn', 'make', 'create']
        actions = [word for word in task_parts if word in action_words]
        
        # Extract targets (nouns after actions)
        for action in actions:
            idx = task_parts.index(action)
            if idx + 1 < len(task_parts):
                components['targets'].append(task_parts[idx + 1])
        
        # Identify conditions
        if 'if' in task_parts:
            idx = task_parts.index('if')
            condition_end = task_parts.index('then') if 'then' in task_parts else len(task_parts)
            components['conditions'].append(' '.join(task_parts[idx+1:condition_end]))
        
        # Build sequence of steps
        current_step = []
        for word in task_parts:
            if word in action_words:
                if current_step:
                    components['sequence'].append(' '.join(current_step))
                current_step = [word]
            else:
                current_step.append(word)
        if current_step:
            components['sequence'].append(' '.join(current_step))
        
        # Add reasoning about the task
        components['reasoning'] = [
            f"This task involves {len(actions)} main actions: {', '.join(actions)}",
            f"The primary targets are: {', '.join(components['targets'])}",
            "This appears to be a " + ("conditional " if components['conditions'] else "") + 
            ("multi-step " if len(components['sequence']) > 1 else "single-step ") + "task"
        ]
        
        return components
    except Exception as e:
        return {'error': str(e)}

def decompose_task(task):
    """Break down complex tasks into simple, executable commands."""
    try:
        # Analyze the task first
        analysis = analyze_task(task)
        
        # Common task patterns
        patterns = {
            'media': ['play', 'watch', 'listen', 'search', 'find'],
            'communication': ['send', 'message', 'call', 'email', 'text'],
            'navigation': ['go', 'open', 'navigate', 'directions'],
            'system': ['set', 'turn', 'change', 'adjust'],
            'information': ['check', 'tell', 'show', 'what']
        }
        
        # Identify task type
        task_types = []
        for type_name, keywords in patterns.items():
            if any(keyword in task.lower() for keyword in keywords):
                task_types.append(type_name)
        
        # Generate execution steps
        steps = []
        
        # Handle media tasks
        if 'media' in task_types:
            if 'youtube' in task.lower():
                query = task.lower().split('youtube')[-1].strip()
                steps.append({
                    'action': 'youtube_search',
                    'query': query,
                    'reasoning': 'User wants to find content on YouTube'
                })
        
        # Handle communication tasks
        if 'communication' in task_types:
            for platform in ['whatsapp', 'message', 'email']:
                if platform in task.lower():
                    # Extract recipient and message
                    parts = task.lower().split(platform)[-1].split('saying')
                    if len(parts) == 2:
                        steps.append({
                            'action': f'{platform}_message',
                            'recipient': parts[0].strip(),
                            'message': parts[1].strip(),
                            'reasoning': f'User wants to send a {platform} message'
                        })
        
        # Handle navigation tasks
        if 'navigation' in task_types:
            for app in ['youtube', 'whatsapp', 'instagram', 'facebook']:
                if app in task.lower():
                    steps.append({
                        'action': 'launch_app',
                        'app': app,
                        'reasoning': f'User wants to use {app}'
                    })
        
        # Add context-aware suggestions
        suggestions = []
        if 'media' in task_types:
            suggestions.append("Would you like me to adjust the volume or brightness?")
        if 'communication' in task_types:
            suggestions.append("Should I set a reminder for follow-up?")
        
        return {
            'analysis': analysis,
            'task_types': task_types,
            'steps': steps,
            'suggestions': suggestions
        }
    except Exception as e:
        return {'error': str(e)}

def process_input(user_input, chat_history="", image_path=None):
    try:
        # First, try to understand if it's a command or conversation
        task_analysis = decompose_task(user_input)
        
        # If it's a clear task with steps
        if task_analysis.get('steps'):
            response_parts = []
            response_parts.append("I understand you want to " + 
                               " and ".join(task_analysis['task_types']) + ".")
            
            # Explain reasoning
            response_parts.append("\nHere's my plan:")
            for i, step in enumerate(task_analysis['steps'], 1):
                response_parts.append(f"{i}. {step['reasoning']}")
            
            # Add suggestions
            if task_analysis['suggestions']:
                response_parts.append("\nSuggestions:")
                for suggestion in task_analysis['suggestions']:
                    response_parts.append(f"- {suggestion}")
            
            # Execute the steps
            for step in task_analysis['steps']:
                process_command(json.dumps(step))
            
            return "\n".join(response_parts)
        
        # If it's not a clear task, process as conversation
        messages = format_messages(user_input, chat_history, image_path)
        
        headers = {
            "Authorization": f"Bearer {OPENROUTER_API_KEY}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/yourusername/javris",
        }
        
        data = {
            "model": OPENROUTER_MODEL,
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 1000,
        }
        
        response = requests.post(OPENROUTER_URL, headers=headers, json=data)
        response.raise_for_status()
        
        result = response.json()
        return result["choices"][0]["message"]["content"]
        
    except Exception as e:
        return f"I encountered an error: {str(e)}"

def analyze_sentiment(text):
    """Enhanced sentiment analysis with emotion detection and intensity."""
    try:
        # Define emotion categories with expanded vocabulary
        emotions = {
            'joy': ['happy', 'excited', 'delighted', 'pleased', 'glad', 'joyful', 'love', 'wonderful', 'fantastic'],
            'sadness': ['sad', 'unhappy', 'depressed', 'down', 'miserable', 'hurt', 'disappointed', 'lonely'],
            'anger': ['angry', 'furious', 'irritated', 'annoyed', 'frustrated', 'mad', 'rage', 'upset'],
            'fear': ['afraid', 'scared', 'worried', 'anxious', 'nervous', 'terrified', 'concerned'],
            'surprise': ['surprised', 'amazed', 'astonished', 'shocked', 'stunned', 'unexpected'],
            'neutral': ['okay', 'fine', 'normal', 'average', 'neutral', 'alright']
        }
        
        # Tokenize and clean text
        words = text.lower().split()
        
        # Count emotion occurrences with intensity
        emotion_scores = {emotion: 0 for emotion in emotions}
        intensity_words = {
            'very': 2.0, 'really': 2.0, 'extremely': 3.0, 'somewhat': 0.5, 'slightly': 0.3
        }
        
        current_multiplier = 1.0
        for i, word in enumerate(words):
            # Check for intensity modifiers
            if word in intensity_words:
                current_multiplier = intensity_words[word]
                continue
                
            # Check emotions
            for emotion, keywords in emotions.items():
                if word in keywords:
                    emotion_scores[emotion] += current_multiplier
            
            current_multiplier = 1.0
        
        # Get dominant emotion
        dominant_emotion = max(emotion_scores.items(), key=lambda x: x[1])
        
        # Calculate overall intensity
        total_emotional_words = sum(emotion_scores.values())
        intensity = total_emotional_words / len(words) if words else 0
        
        return {
            'dominant_emotion': dominant_emotion[0],
            'intensity': intensity,
            'emotion_scores': emotion_scores,
            'detailed_analysis': {
                'word_count': len(words),
                'emotional_words': total_emotional_words,
                'confidence': dominant_emotion[1] / (total_emotional_words if total_emotional_words > 0 else 1)
            }
        }
        
    except Exception as e:
        return {'error': str(e)}

def summarize_day(activities):
    """Enhanced daily summary with more detailed analysis."""
    try:
        # Calculate basic metrics
        summary = {
            'screen_time': sum(act.get('duration', 0) for act in activities if act.get('type') == 'screen'),
            'steps': sum(act.get('steps', 0) for act in activities if act.get('type') == 'movement'),
            'notifications': len([act for act in activities if act.get('type') == 'notification']),
            'apps_used': len(set(act.get('app_name') for act in activities if act.get('app_name'))),
            'productive_time': sum(act.get('duration', 0) for act in activities if act.get('category') == 'productive'),
            'entertainment_time': sum(act.get('duration', 0) for act in activities if act.get('category') == 'entertainment')
        }
        
        # Calculate percentages
        total_time = summary['screen_time']
        if total_time > 0:
            summary['productive_percentage'] = (summary['productive_time'] / total_time) * 100
            summary['entertainment_percentage'] = (summary['entertainment_time'] / total_time) * 100
        
        # Generate insights
        insights = []
        if summary['screen_time'] > 8 * 3600:  # 8 hours
            insights.append("High screen time detected. Consider taking more breaks.")
        if summary['steps'] < 5000:
            insights.append("Daily step count is below recommended. Try to move more.")
        
        # Format insights string
        insights_text = ""
        for insight in insights:
            insights_text += f"- {insight}\n"
        
        return {
            'metrics': summary,
            'insights': insights,
            'formatted_summary': f"""Daily Summary:
- Screen Time: {summary['screen_time'] // 3600} hours {(summary['screen_time'] % 3600) // 60} minutes
- Steps: {summary['steps']}
- Notifications: {summary['notifications']}
- Apps Used: {summary['apps_used']}
- Productive Time: {summary['productive_time'] // 3600} hours
- Entertainment Time: {summary['entertainment_time'] // 3600} hours

Insights:
{insights_text}"""
        }
        
    except Exception as e:
        return {'error': str(e)}

def process_command(command, context=None):
    """Process system commands and automation requests."""
    try:
        # Parse command components
        parts = command.lower().split()
        action = parts[0] if parts else ""
        
        # Define command patterns
        commands = {
            'open': lambda args: {'action': 'open_app', 'app_name': ' '.join(args)},
            'set': lambda args: {'action': 'change_setting', 'setting': args[0], 'value': ' '.join(args[1:])},
            'remind': lambda args: {'action': 'create_reminder', 'text': ' '.join(args)},
            'search': lambda args: {'action': 'web_search', 'query': ' '.join(args)},
            'call': lambda args: {'action': 'make_call', 'contact': ' '.join(args)},
            'message': lambda args: {'action': 'send_message', 'details': ' '.join(args)}
        }
        
        if action in commands and len(parts) > 1:
            return commands[action](parts[1:])
        else:
            return {'error': 'Invalid command format'}
            
    except Exception as e:
        return {'error': str(e)} 