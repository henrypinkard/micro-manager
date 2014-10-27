// Mock device adapter for testing of device sequencing
//
// Copyright (C) 2014 University of California, San Francisco.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida

#pragma once

#include <msgpack.hpp>

#include <boost/make_shared.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/thread.hpp>
#include <boost/weak_ptr.hpp>

#include <map>
#include <string>
#include <vector>


class Serializable
{
public:
   virtual ~Serializable() {}

   // As a rule, writes a single msgpack item
   virtual void Write(msgpack::sbuffer& sbuf) const = 0;
};


class SettingValue : public Serializable
{
public:
   // Use of correct method enforced by LoggedSetting subclasses
   virtual long GetInteger() const { return 0; }
   virtual double GetFloat() const { return 0.0; }
   virtual std::string GetString() const { return std::string(); }
};


class IntegerSettingValue : public SettingValue
{
   const long value_;
public:
   IntegerSettingValue(long value) : value_(value) {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual long GetInteger() const { return value_; }
};


class FloatSettingValue : public SettingValue
{
   const double value_;
public:
   FloatSettingValue(double value) : value_(value) {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual double GetFloat() const { return value_; }
};


class StringSettingValue : public SettingValue
{
   const std::string value_;
public:
   StringSettingValue(const std::string& value) : value_(value) {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   virtual std::string GetString() const { return value_; }
};


class OneShotSettingValue : public SettingValue
{
public:
   OneShotSettingValue() {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
};


class SettingKey : public Serializable
{
   // Note: not const; assignment allowed for use as map key.
   std::string device_;
   std::string key_;
public:
   SettingKey(const std::string& device, const std::string& key) :
      device_(device), key_(key)
   {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
   bool operator<(const SettingKey& rhs) const
   {
      return (this->device_ < rhs.device_) ||
         (this->device_ == rhs.device_ && this->key_ < rhs.key_);
   }
};


class SettingEvent : public Serializable
{
   SettingKey key_;
   boost::shared_ptr<SettingValue> value_;
   size_t count_;
public:
   SettingEvent(SettingKey key, boost::shared_ptr<SettingValue> value,
         uint64_t counterValue) :
      key_(key), value_(value), count_(counterValue)
   {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
};


class CameraInfo : public Serializable
{
   std::string camera_;
   bool isSequence_;
   size_t serialNum_;
   size_t frameNum_;
public:
   CameraInfo(const std::string& camera, bool isSequence,
         size_t serialNum, size_t frameNum) :
      camera_(camera),
      isSequence_(isSequence),
      serialNum_(serialNum),
      frameNum_(frameNum)
   {}
   virtual void Write(msgpack::sbuffer& sbuf) const;
};


class SettingLogger
{
public:
   SettingLogger() :
      counter_(0),
      counterAtLastReset_(0),
      globalImageCount_(0)
   { GuardType g = Guard(); }

   // Locking
   typedef boost::unique_lock<boost::recursive_mutex> GuardType;
   GuardType Guard() const
   { return boost::unique_lock<boost::recursive_mutex>(mutex_); }

   // Recording and querying

   // Set/Fire methods do _not_ mark the device busy
   void SetInteger(const std::string& device, const std::string& key,
         long value, bool logEvent = true);
   long GetInteger(const std::string& device, const std::string& key) const;
   void SetFloat(const std::string& device, const std::string& key,
         double value, bool logEvent = true);
   double GetFloat(const std::string& device, const std::string& key) const;
   void SetString(const std::string& device, const std::string& key,
         const std::string& value, bool logEvent = true);
   std::string GetString(const std::string& device, const std::string& key) const;
   void FireOneShot(const std::string& device, const std::string& key,
         bool logEvent = true);

   void MarkBusy(const std::string& device, bool logEvent = true);
   bool IsBusy(const std::string& device, bool queryNonDestructively = false);

   // Log retrieval
   bool PackAndReset(char* dest, size_t destSize,
         const std::string& camera, bool isSequenceImage,
         size_t cameraSeqNum, size_t acquisitionSeqNum);

private:
   mutable boost::recursive_mutex mutex_; // Protects all data

   uint64_t counter_;
   uint64_t counterAtLastReset_;
   uint64_t globalImageCount_;

   typedef std::map< SettingKey, boost::shared_ptr<SettingValue> > SettingMap;
   typedef SettingMap::const_iterator SettingConstIterator;
   SettingMap settingValues_;
   SettingMap startingValues_;
   std::vector<SettingEvent> settingEvents_;
   std::map<std::string, unsigned> busyPoints_;

   // Helper functions to be called with mutex_ held
   uint64_t GetNextCount() { return counter_++; }
   uint64_t GetNextGlobalImageCount() { return globalImageCount_++; }
   void Reset()
   {
      counterAtLastReset_ = counter_;
      startingValues_ = settingValues_;
      settingEvents_.clear();
   }
   void WriteBusyDevices(msgpack::sbuffer& sbuf) const;
   void WriteSettingMap(msgpack::sbuffer& sbuf, const SettingMap& values) const;
   void WriteHistory(msgpack::sbuffer& sbuf) const;
};